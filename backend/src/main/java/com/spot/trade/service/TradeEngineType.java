package com.spot.trade.service;

public enum TradeEngineType {
    DB,
    KAFKA,
    AERON,
    MEMORY;

    public static TradeEngineType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return DB;
        }
        return TradeEngineType.valueOf(raw.trim().toUpperCase());
    }
}
