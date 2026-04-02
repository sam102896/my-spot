package com.spot.trade.ws;

import com.spot.trade.entity.TradeEntity;
import java.util.Map;

public final class MapPayload {
    private MapPayload() {
    }

    public static Map<String, Object> trade(TradeEntity t) {
        return Map.of("type", "trade", "pairId", t.getPairId().toString(), "price", t.getPrice(), "qty", t.getQty(),
                "quoteQty", t.getQuoteQty(), "createdAt", t.getCreatedAt().toString());
    }

    public static Map<String, Object> bookHint() {
        return Map.of("type", "book");
    }
}
