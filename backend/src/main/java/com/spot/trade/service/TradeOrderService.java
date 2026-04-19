package com.spot.trade.service;

import com.spot.common.web.RequestContext;
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

    record PlaceOrderDetails(String pairSymbol, OrderSide side, OrderType type, String price, String qty,
            String clientOrderId) {
        public static PlaceOrderDetails of(String pairSymbol, OrderSide side, OrderType type, String price, String qty,
                String clientOrderId) {
            return new PlaceOrderDetails(pairSymbol, side, type, price, qty, clientOrderId);
        }
    }

    record PlaceOrderCommand(UUID userId, PlaceOrderDetails details, String idemKey, RequestContext.ClientMeta client) {
        public static PlaceOrderCommand of(UUID userId, PlaceOrderDetails details, String idemKey,
                RequestContext.ClientMeta client) {
            return new PlaceOrderCommand(userId, details, idemKey, client);
        }

        public String pairSymbol() {
            return details.pairSymbol();
        }

        public OrderSide side() {
            return details.side();
        }

        public OrderType type() {
            return details.type();
        }

        public String price() {
            return details.price();
        }

        public String qty() {
            return details.qty();
        }

        public String clientOrderId() {
            return details.clientOrderId();
        }

        public String ip() {
            return client.ip();
        }

        public String deviceId() {
            return client.deviceId();
        }
    }

    record CancelOrderCommand(UUID userId, UUID orderId, RequestContext.ClientMeta client) {
        public static CancelOrderCommand of(UUID userId, UUID orderId, RequestContext.ClientMeta client) {
            return new CancelOrderCommand(userId, orderId, client);
        }

        public String ip() {
            return client.ip();
        }

        public String deviceId() {
            return client.deviceId();
        }
    }
}
