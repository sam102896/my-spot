package com.spot.trade.service;

import com.spot.account.entity.AssetEntity;
import com.spot.account.repo.AssetRepo;
import com.spot.common.api.ApiException;
import com.spot.common.money.Atomic;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradeEntity;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.model.OrderSide;
import com.spot.trade.repo.OrderRepo;
import com.spot.trade.repo.TradeRepo;
import com.spot.trade.repo.TradingPairRepo;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MarketService {
    private final TradingPairRepo pairRepo;
    private final AssetRepo assetRepo;
    private final OrderRepo orderRepo;
    private final TradeRepo tradeRepo;
    private final MarketMakerService marketMakerService;

    public MarketService(TradingPairRepo pairRepo, AssetRepo assetRepo, OrderRepo orderRepo, TradeRepo tradeRepo,
            MarketMakerService marketMakerService) {
        this.pairRepo = pairRepo;
        this.assetRepo = assetRepo;
        this.orderRepo = orderRepo;
        this.tradeRepo = tradeRepo;
        this.marketMakerService = marketMakerService;
    }

    public List<Map<String, Object>> listPairs() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (TradingPairEntity p : pairRepo.findAll()) {
            AssetEntity base = assetRepo.findById(p.getBaseAssetId()).orElse(null);
            AssetEntity quote = assetRepo.findById(p.getQuoteAssetId()).orElse(null);
            out.add(Map.of("symbol", p.getSymbol(), "base",
                    base == null ? p.getBaseAssetId().toString() : base.getSymbol(), "quote",
                    quote == null ? p.getQuoteAssetId().toString() : quote.getSymbol(), "minQty", p.getMinQty(),
                    "minNotional", p.getMinNotional(), "feeBps", p.getFeeBps(), "priceDecimals", p.getPriceDecimals(),
                    "qtyDecimals", p.getQtyDecimals()));
        }
        out.sort(Comparator.comparing(m -> (String) m.get("symbol")));
        return out;
    }

    public TradingPairEntity getPairOrThrow(String symbol) {
        return pairRepo.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PAIR_NOT_FOUND", "交易对不存在"));
    }

    public Map<String, Object> orderBookTop5(String pairSymbol) {
        TradingPairEntity pair = getPairOrThrow(pairSymbol);
        List<OrderEntity> bids = orderRepo.findBidsBook(pair.getId(), PageRequest.of(0, 200));
        List<OrderEntity> asks = orderRepo.findAsksBook(pair.getId(), PageRequest.of(0, 200));
        int levels = 10;
        // 真实委托簿 + 虚拟做市深度合并输出，让前端在冷启动时也有流动性和跳动效果。
        return Map.of("pair", pair.getSymbol(),
                "bids", mergeLevels(aggregateTopLevels(bids, OrderSide.BUY, levels), marketMakerService.bids(pair.getSymbol(), levels),
                        OrderSide.BUY, levels),
                "asks", mergeLevels(aggregateTopLevels(asks, OrderSide.SELL, levels), marketMakerService.asks(pair.getSymbol(), levels),
                        OrderSide.SELL, levels));
    }

    public List<Map<String, Object>> recentTrades(String pairSymbol, int limit) {
        TradingPairEntity pair = getPairOrThrow(pairSymbol);
        List<TradeEntity> ts = tradeRepo.findByPairIdOrderByCreatedAtDesc(pair.getId(),
                PageRequest.of(0, Math.min(limit, 200)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (TradeEntity t : ts) {
            out.add(Map.of("price", t.getPrice(), "qty", t.getQty(), "quoteQty", t.getQuoteQty(), "createdAt",
                    t.getCreatedAt().toString()));
        }
        for (MarketMakerService.TradeSnapshot t : marketMakerService.recentTrades(pair.getSymbol(), limit)) {
            out.add(Map.of("price", t.price(), "qty", t.qty(), "quoteQty", t.quoteQty(), "createdAt",
                    t.createdAt().toString()));
        }
        out.sort(Comparator.comparing((Map<String, Object> m) -> Instant.parse((String) m.get("createdAt"))).reversed());
        return out.subList(0, Math.min(limit, out.size()));
    }

    public List<Map<String, Object>> kline1m(String pairSymbol, int limit) {
        TradingPairEntity pair = getPairOrThrow(pairSymbol);
        List<TradeEntity> ts = tradeRepo.findByPairIdOrderByCreatedAtDesc(pair.getId(), PageRequest.of(0, 2000));
        long interval = 60L;
        LinkedHashMap<Long, Bar> bars = new LinkedHashMap<>();
        // K 线同时吸收真实成交和模拟成交，保证无人交易时图表也能连续更新。
        for (int i = ts.size() - 1; i >= 0; i--) {
            TradeEntity t = ts.get(i);
            applyTradePoint(bars, interval, t.getCreatedAt(), t.getPrice(), t.getQty());
        }
        List<MarketMakerService.TradeSnapshot> synthetic = marketMakerService.recentTrades(pair.getSymbol(), 2400);
        for (int i = synthetic.size() - 1; i >= 0; i--) {
            MarketMakerService.TradeSnapshot t = synthetic.get(i);
            applyTradePoint(bars, interval, t.createdAt(), t.price(), t.qty());
        }
        List<Long> keys = new ArrayList<>(bars.keySet());
        int from = Math.max(0, keys.size() - Math.min(limit, 200));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = from; i < keys.size(); i++) {
            Bar b = bars.get(keys.get(i));
            out.add(b.toMap());
        }
        return out;
    }

    private void applyTradePoint(LinkedHashMap<Long, Bar> bars, long interval, Instant createdAt, long price, long qty) {
        long epoch = createdAt.getEpochSecond();
        long bucket = epoch - (epoch % interval);
        Bar b = bars.computeIfAbsent(bucket, Bar::new);
        b.apply(price, qty);
    }

    private List<Map<String, Object>> aggregateTopLevels(List<OrderEntity> orders, OrderSide side, int levels) {
        LinkedHashMap<Long, Long> byPrice = new LinkedHashMap<>();
        for (OrderEntity o : orders) {
            if (o.getPrice() == null) {
                continue;
            }
            long remaining = o.getOrigQty() - o.getFilledQty();
            if (remaining <= 0) {
                continue;
            }
            byPrice.merge(o.getPrice(), remaining, Long::sum);
            if (byPrice.size() >= levels && side == OrderSide.SELL) {
                continue;
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        int n = 0;
        for (var e : byPrice.entrySet()) {
            out.add(Map.of("price", e.getKey(), "qty", e.getValue()));
            n++;
            if (n >= levels) {
                break;
            }
        }
        return out;
    }

    private List<Map<String, Object>> mergeLevels(List<Map<String, Object>> real,
            List<MarketMakerService.LevelSnapshot> synthetic, OrderSide side, int levels) {
        TreeMap<Long, Long> merged = side == OrderSide.BUY ? new TreeMap<Long, Long>(Comparator.reverseOrder())
                : new TreeMap<Long, Long>(Comparator.naturalOrder());
        for (Map<String, Object> row : real) {
            long price = ((Number) row.get("price")).longValue();
            long qty = ((Number) row.get("qty")).longValue();
            merged.merge(price, qty, Long::sum);
        }
        for (MarketMakerService.LevelSnapshot row : synthetic) {
            merged.merge(row.price(), row.qty(), Long::sum);
        }
        ArrayList<Map<String, Object>> out = new ArrayList<>();
        int i = 0;
        for (Map.Entry<Long, Long> e : merged.entrySet()) {
            out.add(Map.of("price", e.getKey(), "qty", e.getValue()));
            i++;
            if (i >= levels) {
                break;
            }
        }
        return out;
    }

    private static final class Bar {
        private final long bucket;
        private long open;
        private long high;
        private long low;
        private long close;
        private long volume;

        private Bar(long bucket) {
            this.bucket = bucket;
        }

        private void apply(TradeEntity t) {
            apply(t.getPrice(), t.getQty());
        }

        private void apply(long price, long qty) {
            if (volume == 0) {
                open = price;
                high = price;
                low = price;
            }
            high = Math.max(high, price);
            low = Math.min(low, price);
            close = price;
            volume += qty;
        }

        private Map<String, Object> toMap() {
            return Map.of("t", Instant.ofEpochSecond(bucket).atZone(ZoneOffset.UTC).toInstant().toString(), "o", open,
                    "h", high, "l", low, "c", close, "v", volume);
        }
    }
}
