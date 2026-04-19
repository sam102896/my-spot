package com.spot.trade.domain.model;

import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.service.TradeOrderService;
import java.time.Instant;

public record TradeOrderDraft(TradingPairEntity pair, TradeOrderService.PlaceOrderCommand command, Long priceAtomic,
        long qtyAtomic, long reservedQuote) {
    public static TradeOrderDraft of(TradingPairEntity pair, TradeOrderService.PlaceOrderCommand command,
            Long priceAtomic, long qtyAtomic, long reservedQuote) {
        return new TradeOrderDraft(pair, command, priceAtomic, qtyAtomic, reservedQuote);
    }

    public OrderEntity toEntity(Instant now) {
        OrderEntity order = new OrderEntity();
        order.setUserId(command.userId());
        order.setPairId(pair.getId());
        order.setClientOrderId(command.clientOrderId());
        order.setSide(command.side());
        order.setType(command.type());
        order.setPrice(priceAtomic);
        order.setOrigQty(qtyAtomic);
        order.setFilledQty(0);
        order.setReservedQuote(reservedQuote);
        order.setStatus(com.spot.trade.model.OrderStatus.NEW);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        return order;
    }
}
