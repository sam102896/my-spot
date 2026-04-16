package com.spot.trade.api;

import com.spot.trade.service.MarketService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/market")
public class PublicMarketController {
    private final MarketService marketService;

    public PublicMarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping("/pairs")
    public List<PairRes> pairs() {
        return marketService.listPairs().stream().map(this::toPairRes).toList();
    }

    @GetMapping("/orderbook")
    public OrderBookRes orderbook(@RequestParam("pair") String pair) {
        return toOrderBookRes(marketService.orderBookTop5(pair));
    }

    @GetMapping("/trades")
    public List<TradeRes> trades(@RequestParam("pair") String pair,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return marketService.recentTrades(pair, limit).stream().map(this::toTradeRes).toList();
    }

    @GetMapping("/kline")
    public List<KlineBarRes> kline(@RequestParam("pair") String pair,
            @RequestParam(value = "limit", defaultValue = "120") int limit) {
        return marketService.kline1m(pair, limit).stream().map(this::toKlineBarRes).toList();
    }

    private PairRes toPairRes(Map<String, Object> row) {
        return new PairRes((String) row.get("symbol"), (String) row.get("base"), (String) row.get("quote"),
                num(row.get("minQty")), num(row.get("minNotional")), intNum(row.get("feeBps")),
                intNum(row.get("priceDecimals")), intNum(row.get("qtyDecimals")));
    }

    private OrderBookRes toOrderBookRes(Map<String, Object> row) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> bids = (List<Map<String, Object>>) row.get("bids");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> asks = (List<Map<String, Object>>) row.get("asks");
        return new OrderBookRes((String) row.get("pair"), bids.stream().map(this::toLevelRes).toList(),
                asks.stream().map(this::toLevelRes).toList());
    }

    private LevelRes toLevelRes(Map<String, Object> row) {
        return new LevelRes(num(row.get("price")), num(row.get("qty")));
    }

    private TradeRes toTradeRes(Map<String, Object> row) {
        return new TradeRes(num(row.get("price")), num(row.get("qty")), num(row.get("quoteQty")),
                (String) row.get("createdAt"));
    }

    private KlineBarRes toKlineBarRes(Map<String, Object> row) {
        return new KlineBarRes((String) row.get("t"), num(row.get("o")), num(row.get("h")), num(row.get("l")),
                num(row.get("c")), num(row.get("v")));
    }

    private long num(Object value) {
        return ((Number) value).longValue();
    }

    private int intNum(Object value) {
        return ((Number) value).intValue();
    }

    public record PairRes(String symbol, String base, String quote, long minQty, long minNotional, int feeBps,
            int priceDecimals, int qtyDecimals) {
    }

    public record OrderBookRes(String pair, List<LevelRes> bids, List<LevelRes> asks) {
    }

    public record LevelRes(long price, long qty) {
    }

    public record TradeRes(long price, long qty, long quoteQty, String createdAt) {
    }

    public record KlineBarRes(String t, long o, long h, long l, long c, long v) {
    }
}
