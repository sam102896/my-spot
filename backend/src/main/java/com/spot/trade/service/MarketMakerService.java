package com.spot.trade.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spot.common.money.Atomic;
import com.spot.config.AppProperties;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.repo.TradingPairRepo;
import com.spot.trade.ws.MarketHub;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class MarketMakerService {
    private static final Logger log = LoggerFactory.getLogger(MarketMakerService.class);
    private static final long SCALE = Atomic.scale(Atomic.DEFAULT_DECIMALS);

    private final TradingPairRepo pairRepo;
    private final MarketHub marketHub;
    private final AppProperties appProperties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ConcurrentHashMap<String, PairState> states = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private volatile WebSocket binanceWs;
    private volatile Instant lastBinanceMessageAt = Instant.EPOCH;
    private volatile String currentStreamKey = "";

    public MarketMakerService(TradingPairRepo pairRepo, MarketHub marketHub, AppProperties appProperties,
            ObjectMapper mapper) {
        this.pairRepo = pairRepo;
        this.marketHub = marketHub;
        this.appProperties = appProperties;
        this.mapper = mapper;
    }

    @PostConstruct
    public void init() {
        refreshPairs();
        connectBinanceIfNeeded();
    }

    @PreDestroy
    public void destroy() {
        closeBinance();
    }

    public boolean isEnabled() {
        return appProperties.getTrading().getMarketMaker().isEnabled();
    }

    public List<LevelSnapshot> bids(String pair, int limit) {
        return sideSnapshot(pair, true, limit);
    }

    public List<LevelSnapshot> asks(String pair, int limit) {
        return sideSnapshot(pair, false, limit);
    }

    public List<TradeSnapshot> recentTrades(String pair, int limit) {
        if (!isEnabled()) {
            return List.of();
        }
        PairState state = states.get(pair.toUpperCase());
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            ArrayList<TradeSnapshot> out = new ArrayList<>();
            int capped = Math.max(0, Math.min(limit, 500));
            int i = 0;
            for (TradeSnapshot t : state.trades) {
                out.add(t);
                i++;
                if (i >= capped) {
                    break;
                }
            }
            return out;
        }
    }

    @Scheduled(fixedDelayString = "${app.trading.marketMaker.refreshMs:1200}")
    public void refreshBooks() {
        if (!isEnabled()) {
            return;
        }
        // 周期性刷新虚拟盘口：优先跟随 Binance 中间价，失败时退化为本地随机游走。
        refreshPairs();
        for (TradingPairEntity pair : pairRepo.findAll()) {
            PairState state = states.computeIfAbsent(pair.getSymbol(), k -> seedState(pair));
            boolean changed;
            synchronized (state) {
                long prevBid = state.bestBid;
                long prevAsk = state.bestAsk;
                state.midPrice = nextMid(pair, state);
                rebuildBook(pair, state);
                changed = prevBid != state.bestBid || prevAsk != state.bestAsk;
            }
            if (changed) {
                marketHub.publish(pair.getSymbol(), Map.of("type", "book"));
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.trading.marketMaker.tradePulseMs:1500}")
    public void pulseTrades() {
        if (!isEnabled()) {
            return;
        }
        // 周期性生成虚拟成交，驱动前端“最新成交/K线/价格跳动”持续刷新。
        refreshPairs();
        for (TradingPairEntity pair : pairRepo.findAll()) {
            PairState state = states.computeIfAbsent(pair.getSymbol(), k -> seedState(pair));
            TradeSnapshot trade;
            synchronized (state) {
                if (state.bestBid <= 0 || state.bestAsk <= 0) {
                    rebuildBook(pair, state);
                }
                long spread = Math.max(priceTick(pair), state.bestAsk - state.bestBid);
                long jitter = randomBetween(-Math.max(priceTick(pair), spread / 3), Math.max(priceTick(pair), spread / 3));
                long price = alignPrice(pair, clamp(state.bestBid + spread / 2 + jitter, priceTick(pair), Long.MAX_VALUE));
                long qty = randomQty(pair);
                trade = new TradeSnapshot(pair.getSymbol(), price, qty, Atomic.quoteQtyFromPriceQty(price, qty),
                        Instant.now(), "MM");
                state.midPrice = price;
                state.addTrade(trade);
                rebuildBook(pair, state);
            }
            marketHub.publish(pair.getSymbol(), Map.of("type", "trade", "price", trade.price(), "qty", trade.qty(),
                    "quoteQty", trade.quoteQty(), "createdAt", trade.createdAt().toString(), "source",
                    trade.source()));
            marketHub.publish(pair.getSymbol(), Map.of("type", "book"));
        }
    }

    @Scheduled(fixedDelayString = "${app.trading.marketMaker.binanceWatchdogMs:15000}")
    public void superviseBinance() {
        if (!isEnabled() || !appProperties.getTrading().getMarketMaker().isUseBinancePrice()) {
            return;
        }
        refreshPairs();
        if (Duration.between(lastBinanceMessageAt, Instant.now()).toMillis() > appProperties.getTrading()
                .getMarketMaker().getBinanceWatchdogMs()) {
            connectBinanceIfNeeded();
        }
    }

    private List<LevelSnapshot> sideSnapshot(String pair, boolean bid, int limit) {
        if (!isEnabled()) {
            return List.of();
        }
        PairState state = states.get(pair.toUpperCase());
        if (state == null) {
            return List.of();
        }
        synchronized (state) {
            List<LevelSnapshot> source = bid ? state.bids : state.asks;
            int capped = Math.max(0, Math.min(limit, source.size()));
            return new ArrayList<>(source.subList(0, capped));
        }
    }

    private void refreshPairs() {
        for (TradingPairEntity pair : pairRepo.findAll()) {
            states.computeIfAbsent(pair.getSymbol(), k -> seedState(pair));
        }
    }

    private PairState seedState(TradingPairEntity pair) {
        PairState state = new PairState(defaultSeedPrice(pair.getSymbol()));
        synchronized (state) {
            // 启动时预灌一段历史成交，确保前端首次打开就有 K 线和最近成交数据。
            long px = state.midPrice;
            Instant now = Instant.now();
            for (int i = 240; i >= 1; i--) {
                px = alignPrice(pair, clamp(px + randomBetween(-px / 2500, px / 2500), priceTick(pair), Long.MAX_VALUE));
                long qty = randomQty(pair);
                state.addTrade(new TradeSnapshot(pair.getSymbol(), px, qty, Atomic.quoteQtyFromPriceQty(px, qty),
                        now.minusSeconds(i * 60L), "BOOT"));
            }
            state.midPrice = px;
            rebuildBook(pair, state);
        }
        return state;
    }

    private long nextMid(TradingPairEntity pair, PairState state) {
        if (appProperties.getTrading().getMarketMaker().isUseBinancePrice() && state.externalBid > 0
                && state.externalAsk > state.externalBid) {
            long externalMid = (state.externalBid + state.externalAsk) / 2;
            if (state.midPrice <= 0) {
                return externalMid;
            }
            return alignPrice(pair, (state.midPrice * 7 + externalMid * 3) / 10);
        }
        if (state.midPrice <= 0) {
            return defaultSeedPrice(pair.getSymbol());
        }
        long drift = randomBetween(-Math.max(priceTick(pair), state.midPrice / 3000),
                Math.max(priceTick(pair), state.midPrice / 3000));
        return alignPrice(pair, clamp(state.midPrice + drift, priceTick(pair), Long.MAX_VALUE));
    }

    private void rebuildBook(TradingPairEntity pair, PairState state) {
        int levels = Math.max(5, appProperties.getTrading().getMarketMaker().getLevels());
        long tick = priceTick(pair);
        long minSpread = Math.max(tick, state.midPrice * Math.max(1, appProperties.getTrading().getMarketMaker().getSpreadBps()) / 10000);
        long bestBid;
        long bestAsk;

        if (appProperties.getTrading().getMarketMaker().isUseBinancePrice() && state.externalBid > 0
                && state.externalAsk > state.externalBid) {
            // 有外部报价时，用外部最优买卖价作为第一档，再向外扩展深度。
            bestBid = alignPrice(pair, state.externalBid);
            bestAsk = alignPrice(pair, Math.max(state.externalAsk, bestBid + tick));
        } else {
            // 没有外部价格时，用内部中间价加固定 spread 构造深度。
            long half = Math.max(tick, minSpread / 2);
            bestBid = alignPrice(pair, Math.max(tick, state.midPrice - half));
            bestAsk = alignPrice(pair, Math.max(bestBid + tick, state.midPrice + half));
        }

        long step = Math.max(tick, Math.max(minSpread, bestAsk - bestBid) / 2);
        ArrayList<LevelSnapshot> bids = new ArrayList<>();
        ArrayList<LevelSnapshot> asks = new ArrayList<>();
        for (int i = 0; i < levels; i++) {
            long bidPx = alignPrice(pair, Math.max(tick, bestBid - (long) i * step));
            long askPx = alignPrice(pair, bestAsk + (long) i * step);
            bids.add(new LevelSnapshot(bidPx, randomQty(pair)));
            asks.add(new LevelSnapshot(askPx, randomQty(pair)));
        }

        state.bestBid = bestBid;
        state.bestAsk = bestAsk;
        state.bids = bids;
        state.asks = asks;
    }

    private long randomQty(TradingPairEntity pair) {
        long min = Math.max(pair.getMinQty(), parseAtomic(appProperties.getTrading().getMarketMaker().getMinQty()));
        long max = Math.max(min, parseAtomic(appProperties.getTrading().getMarketMaker().getMaxQty()));
        if (max <= min) {
            return alignQty(pair, min);
        }
        long raw = min + Math.abs(random.nextLong()) % Math.max(1L, max - min + 1L);
        return alignQty(pair, raw);
    }

    private long defaultSeedPrice(String pair) {
        if ("ETHUSDT".equalsIgnoreCase(pair)) {
            return parseAtomic("3500");
        }
        if ("BNBUSDT".equalsIgnoreCase(pair)) {
            return parseAtomic("600");
        }
        return parseAtomic("68000");
    }

    private long parseAtomic(String value) {
        try {
            return Atomic.parse(value, Atomic.DEFAULT_DECIMALS);
        } catch (Exception e) {
            return SCALE;
        }
    }

    private long alignPrice(TradingPairEntity pair, long priceAtomic) {
        long tick = priceTick(pair);
        return Math.max(tick, (priceAtomic / tick) * tick);
    }

    private long alignQty(TradingPairEntity pair, long qtyAtomic) {
        long tick = qtyTick(pair);
        return Math.max(tick, (qtyAtomic / tick) * tick);
    }

    private long priceTick(TradingPairEntity pair) {
        return unitByDecimals(pair.getPriceDecimals());
    }

    private long qtyTick(TradingPairEntity pair) {
        return unitByDecimals(pair.getQtyDecimals());
    }

    private long unitByDecimals(int decimals) {
        int keep = Math.max(0, Math.min(Atomic.DEFAULT_DECIMALS, decimals));
        return BigDecimal.TEN.pow(Atomic.DEFAULT_DECIMALS - keep).longValueExact();
    }

    private long clamp(long n, long min, long max) {
        return Math.max(min, Math.min(max, n));
    }

    private long randomBetween(long min, long max) {
        if (max <= min) {
            return min;
        }
        long bound = max - min + 1L;
        long v = Math.abs(random.nextLong());
        return min + (v % bound);
    }

    private void connectBinanceIfNeeded() {
        if (!isEnabled() || !appProperties.getTrading().getMarketMaker().isUseBinancePrice()) {
            closeBinance();
            return;
        }
        ArrayList<String> streams = new ArrayList<>();
        for (TradingPairEntity pair : pairRepo.findAll()) {
            streams.add(pair.getSymbol().toLowerCase() + "@bookTicker");
        }
        if (streams.isEmpty()) {
            return;
        }
        String streamKey = String.join("/", streams);
        if (binanceWs != null && streamKey.equals(currentStreamKey)) {
            return;
        }

        closeBinance();
        currentStreamKey = streamKey;
        // 直接监听 Binance bookTicker，取最优买卖价来锚定本地做市盘口。
        String url = "wss://stream.binance.com:9443/stream?streams=" + streamKey;
        httpClient.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(10)).buildAsync(URI.create(url),
                new BinanceListener()).thenAccept(ws -> {
                    binanceWs = ws;
                    lastBinanceMessageAt = Instant.now();
                    log.info("Binance price feed connected: {}", currentStreamKey);
                }).exceptionally(ex -> {
                    binanceWs = null;
                    log.warn("Binance price feed unavailable, fallback to local market maker: {}", ex.getMessage());
                    return null;
                });
    }

    private void closeBinance() {
        WebSocket ws = binanceWs;
        binanceWs = null;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
            } catch (Exception ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void onBinanceMessage(String payload) {
        try {
            Map<String, Object> root = mapper.readValue(payload, Map.class);
            Object dataObj = root.get("data");
            if (!(dataObj instanceof Map<?, ?> data)) {
                return;
            }
            Object symbolObj = data.get("s");
            Object bidObj = data.get("b");
            Object askObj = data.get("a");
            if (!(symbolObj instanceof String symbol) || !(bidObj instanceof String bid) || !(askObj instanceof String ask)) {
                return;
            }
            PairState state = states.get(symbol.toUpperCase());
            if (state == null) {
                return;
            }
            synchronized (state) {
                state.externalBid = Atomic.parse(bid, Atomic.DEFAULT_DECIMALS);
                state.externalAsk = Atomic.parse(ask, Atomic.DEFAULT_DECIMALS);
            }
            lastBinanceMessageAt = Instant.now();
        } catch (Exception ignored) {
        }
    }

    public record LevelSnapshot(long price, long qty) {
    }

    public record TradeSnapshot(String pair, long price, long qty, long quoteQty, Instant createdAt, String source) {
    }

    private static final class PairState {
        private long midPrice;
        private long bestBid;
        private long bestAsk;
        private long externalBid;
        private long externalAsk;
        private List<LevelSnapshot> bids = List.of();
        private List<LevelSnapshot> asks = List.of();
        private final ArrayDeque<TradeSnapshot> trades = new ArrayDeque<>();

        private PairState(long midPrice) {
            this.midPrice = midPrice;
        }

        private void addTrade(TradeSnapshot trade) {
            trades.addFirst(trade);
            while (trades.size() > 2400) {
                trades.removeLast();
            }
        }
    }

    private final class BinanceListener implements WebSocket.Listener {
        private final StringBuilder buf = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            WebSocket.Listener.super.onOpen(webSocket);
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buf.append(data);
            if (last) {
                onBinanceMessage(buf.toString());
                buf.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("Binance price feed error: {}", error.getMessage());
            binanceWs = null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.warn("Binance price feed closed: {} {}", statusCode, reason);
            binanceWs = null;
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }
    }
}
