package com.spot.trade.api;

import com.spot.common.web.RequestContext;
import com.spot.security.Auth;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.model.OrderSide;
import com.spot.trade.model.OrderType;
import com.spot.trade.service.TradingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
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
    private final TradingService tradingService;

    public TradeController(TradingService tradingService) {
        this.tradingService = tradingService;
    }

    @PostMapping("/order")
    public Map<String, Object> place(@RequestBody PlaceOrderReq req, @RequestHeader("X-Idempotency-Key") String idemKey,
            HttpServletRequest http) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        OrderEntity o = tradingService.placeOrder(userId, req.pair, OrderSide.valueOf(req.side),
                OrderType.valueOf(req.type), req.price, req.qty, req.clientOrderId, idemKey, RequestContext.ip(http),
                RequestContext.deviceId(http));
        return Map.of("id", o.getId().toString(), "status", o.getStatus().name(), "filledQty", o.getFilledQty(),
                "origQty", o.getOrigQty(), "reservedQuote", o.getReservedQuote());
    }

    @PostMapping("/order/{id}/cancel")
    public Map<String, Object> cancel(@PathVariable("id") String id, HttpServletRequest http) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        OrderEntity o = tradingService.cancel(userId, UUID.fromString(id), RequestContext.ip(http),
                RequestContext.deviceId(http));
        return Map.of("id", o.getId().toString(), "status", o.getStatus().name());
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
    }
}
