package com.spot.trade.api;

import com.spot.common.web.RequestContext;
import com.spot.security.Auth;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.model.OrderSide;
import com.spot.trade.model.OrderType;
import com.spot.trade.service.TradeOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trade")
public class TradeController {
    private final TradeOrderService tradingService;

    public TradeController(TradeOrderService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping("/order")
    public PlaceOrderRes place(@RequestBody PlaceOrderReq req, @RequestHeader("X-Idempotency-Key") String idemKey,
            HttpServletRequest http) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        OrderEntity o = tradingService.placeOrder(req.toCommand(userId, idemKey, RequestContext.capture(http)));
        return new PlaceOrderRes(o.getId().toString(), o.getStatus().name(), o.getFilledQty(), o.getOrigQty(),
                o.getReservedQuote());
    }

    @PostMapping("/order/{id}/cancel")
    public CancelOrderRes cancel(@PathVariable("id") String id, HttpServletRequest http) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        OrderEntity o = tradingService
                .cancelOrder(TradeOrderService.CancelOrderCommand.of(userId, UUID.fromString(id), RequestContext.capture(http)));
        return new CancelOrderRes(o.getId().toString(), o.getStatus().name());
    }

    @GetMapping("/open-orders")
    public List<OrderEntity> openOrders(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        return tradingService.openOrders(userId, limit);
    }

    @GetMapping("/orders")
    public List<OrderEntity> orders(@RequestParam(value = "limit", defaultValue = "100") int limit) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        return tradingService.orderHistory(userId, limit);
    }

    public record PlaceOrderReq(@NotBlank String pair, @NotBlank String side, @NotBlank String type, String price,
            @NotBlank String qty, String clientOrderId) {
        public TradeOrderService.PlaceOrderCommand toCommand(UUID userId, String idemKey, RequestContext.ClientMeta client) {
            return TradeOrderService.PlaceOrderCommand.of(userId,
                    TradeOrderService.PlaceOrderDetails.of(pair, OrderSide.valueOf(side), OrderType.valueOf(type), price,
                            qty, clientOrderId),
                    idemKey, client);
        }
    }

    public record PlaceOrderRes(String id, String status, long filledQty, long origQty, long reservedQuote) {
    }

    public record CancelOrderRes(String id, String status) {
    }
}
