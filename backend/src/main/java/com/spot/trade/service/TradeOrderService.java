package com.spot.trade.service;

import com.spot.trade.entity.OrderEntity;
import com.spot.trade.model.OrderSide;
import com.spot.trade.model.OrderType;
import java.util.List;
import java.util.UUID;

public interface TradeOrderService {
    OrderEntity placeOrder(PlaceOrderCommand command);

    OrderEntity cancelOrder(CancelOrderCommand command);

    List<OrderEntity> openOrders(UUID userId, int limit);

    List<OrderEntity> orderHistory(UUID userId, int limit);

    record PlaceOrderCommand(UUID userId, String pairSymbol, OrderSide side, OrderType type, String price, String qty,
            String clientOrderId, String idemKey, String ip, String deviceId) {
    }

    record CancelOrderCommand(UUID userId, UUID orderId, String ip, String deviceId) {
    }
}
