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

    public MarketService(TradingPairRepo pairRepo, AssetRepo assetRepo, OrderRepo orderRepo, TradeRepo tradeRepo) {
        this.pairRepo = pairRepo;
        this.assetRepo = assetRepo;
        this.orderRepo = orderRepo;
        this.tradeRepo = tradeRepo;
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
        return Map.of("pair", pair.getSymbol(), "bids", aggregateTopLevels(bids, OrderSide.BUY, 5), "asks",
                aggregateTopLevels(asks, OrderSide.SELL, 5));
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
        return out;
    }

    public List<Map<String, Object>> kline1m(String pairSymbol, int limit) {
        TradingPairEntity pair = getPairOrThrow(pairSymbol);
        List<TradeEntity> ts = tradeRepo.findByPairIdOrderByCreatedAtDesc(pair.getId(), PageRequest.of(0, 2000));
        long interval = 60L;
        LinkedHashMap<Long, Bar> bars = new LinkedHashMap<>();
        for (int i = ts.size() - 1; i >= 0; i--) {
            TradeEntity t = ts.get(i);
            long epoch = t.getCreatedAt().getEpochSecond();
            long bucket = epoch - (epoch % interval);
            Bar b = bars.computeIfAbsent(bucket, Bar::new);
            b.apply(t);
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
            if (volume == 0) {
                open = t.getPrice();
                high = t.getPrice();
                low = t.getPrice();
            }
            high = Math.max(high, t.getPrice());
            low = Math.min(low, t.getPrice());
            close = t.getPrice();
            volume += t.getQty();
        }

        private Map<String, Object> toMap() {
            return Map.of("t", Instant.ofEpochSecond(bucket).atZone(ZoneOffset.UTC).toInstant().toString(), "o", open,
                    "h", high, "l", low, "c", close, "v", volume);
        }
    }
}
