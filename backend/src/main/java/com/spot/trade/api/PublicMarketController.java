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
    public List<Map<String, Object>> pairs() {
        return marketService.listPairs();
    }

    @GetMapping("/orderbook")
    public Map<String, Object> orderbook(@RequestParam("pair") String pair) {
        return marketService.orderBookTop5(pair);
    }

    @GetMapping("/trades")
    public List<Map<String, Object>> trades(@RequestParam("pair") String pair,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return marketService.recentTrades(pair, limit);
    }

    @GetMapping("/kline")
    public List<Map<String, Object>> kline(@RequestParam("pair") String pair,
            @RequestParam(value = "limit", defaultValue = "120") int limit) {
        return marketService.kline1m(pair, limit);
    }
}
