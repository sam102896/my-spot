package com.spot.trade.domain.model;

import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradingPairEntity;

public record CancelOrderDecision(OrderEntity order, TradingPairEntity pair, long remainingQty) {
    public static CancelOrderDecision of(OrderEntity order, TradingPairEntity pair, long remainingQty) {
        return new CancelOrderDecision(order, pair, remainingQty);
    }
}
