package com.spot.trade.domain.service;

import com.spot.common.api.ApiException;
import com.spot.trade.domain.model.CancelOrderDecision;
import com.spot.trade.domain.model.TradeOrderDraft;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.model.OrderSide;
import com.spot.trade.model.OrderStatus;
import com.spot.trade.model.OrderType;
import com.spot.trade.service.DbTradePositionService;
import com.spot.trade.service.MatchingEngine;
import com.spot.trade.service.TradeOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class TradeOrderDomainService {
    private final MatchingEngine matchingEngine;
    private final DbTradePositionService positionService;

    public TradeOrderDomainService(MatchingEngine matchingEngine, DbTradePositionService positionService) {
        this.matchingEngine = matchingEngine;
        this.positionService = positionService;
    }

    public TradeOrderDraft draft(TradeOrderService.PlaceOrderCommand command, TradingPairEntity pair, Long priceAtomic,
            long qtyAtomic) {
        long reserveQuote = 0;
        if (command.side() == OrderSide.BUY) {
            reserveQuote = command.type() == OrderType.LIMIT
                    ? matchingEngine.requiredQuoteForBuyLimit(priceAtomic, qtyAtomic, pair.getFeeBps())
                    : positionService.estimateMarketBuyReserve(pair.getId(), qtyAtomic, pair.getFeeBps());
            if (command.type() == OrderType.MARKET && reserveQuote < pair.getMinNotional()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MIN_NOTIONAL", "小于最小交易金额");
            }
        }
        return TradeOrderDraft.of(pair, command, priceAtomic, qtyAtomic, reserveQuote);
    }

    public CancelOrderDecision prepareCancel(OrderEntity order, TradingPairEntity pair, java.util.UUID userId) {
        if (!order.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权限");
        }
        if (order.getStatus() == OrderStatus.CANCELED) {
            return CancelOrderDecision.of(order, pair, order.getOrigQty() - order.getFilledQty());
        }
        if (order.getStatus() == OrderStatus.FILLED || order.getStatus() == OrderStatus.REJECTED) {
            throw new ApiException(HttpStatus.CONFLICT, "ORDER_NOT_CANCELABLE", "该状态无法撤单");
        }
        return CancelOrderDecision.of(order, pair, order.getOrigQty() - order.getFilledQty());
    }
}
