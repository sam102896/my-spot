package com.spot.trade.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.spot.common.api.ApiException;
import com.spot.common.web.RequestContext;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.model.OrderSide;
import com.spot.trade.model.OrderType;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public final class KafkaTradeMessages {
    private KafkaTradeMessages() {
    }

    public record TradeCommandMessage(String requestId, String action, String key, PlaceOrderPayload placeOrder,
            CancelOrderPayload cancelOrder) {
        public static TradeCommandMessage place(String requestId, TradeOrderService.PlaceOrderCommand command) {
            return new TradeCommandMessage(requestId, "PLACE", command.pairSymbol(),
                    PlaceOrderPayload.from(command), null);
        }

        public static TradeCommandMessage cancel(String requestId, TradeOrderService.CancelOrderCommand command) {
            return new TradeCommandMessage(requestId, "CANCEL", command.orderId().toString(), null,
                    CancelOrderPayload.from(command));
        }
    }

    public record PlaceOrderPayload(UUID userId, String pairSymbol, OrderSide side, OrderType type, String price,
            String qty, String clientOrderId, String idemKey, ClientPayload client) {
        public static PlaceOrderPayload from(TradeOrderService.PlaceOrderCommand command) {
            return new PlaceOrderPayload(command.userId(), command.pairSymbol(), command.side(), command.type(),
                    command.price(), command.qty(), command.clientOrderId(), command.idemKey(),
                    ClientPayload.from(command.client()));
        }

        public TradeOrderService.PlaceOrderCommand toCommand() {
            return TradeOrderService.PlaceOrderCommand.of(userId,
                    TradeOrderService.PlaceOrderDetails.of(pairSymbol, side, type, price, qty, clientOrderId),
                    idemKey, client.toClientMeta());
        }
    }

    public record CancelOrderPayload(UUID userId, UUID orderId, ClientPayload client) {
        public static CancelOrderPayload from(TradeOrderService.CancelOrderCommand command) {
            return new CancelOrderPayload(command.userId(), command.orderId(), ClientPayload.from(command.client()));
        }

        public TradeOrderService.CancelOrderCommand toCommand() {
            return TradeOrderService.CancelOrderCommand.of(userId, orderId, client.toClientMeta());
        }
    }

    public record ClientPayload(String ip, String deviceId) {
        public static ClientPayload from(RequestContext.ClientMeta client) {
            return new ClientPayload(client.ip(), client.deviceId());
        }

        public RequestContext.ClientMeta toClientMeta() {
            return new RequestContext.ClientMeta(ip, deviceId);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TradeResultMessage(String requestId, boolean success, UUID orderId, int status, String errorCode,
            String errorMessage) {
        public static TradeResultMessage success(String requestId, OrderEntity order) {
            return new TradeResultMessage(requestId, true, order.getId(), HttpStatus.OK.value(), null, null);
        }

        public static TradeResultMessage failure(String requestId, ApiException exception) {
            return new TradeResultMessage(requestId, false, null, exception.getStatus().value(), exception.getCode(),
                    exception.getMessage());
        }

        public static TradeResultMessage failure(String requestId, Exception exception) {
            return new TradeResultMessage(requestId, false, null, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "TRADE_KAFKA_ERROR", exception.getMessage());
        }

        public ApiException toApiException() {
            return new ApiException(HttpStatus.valueOf(status), errorCode, errorMessage);
        }
    }
}
