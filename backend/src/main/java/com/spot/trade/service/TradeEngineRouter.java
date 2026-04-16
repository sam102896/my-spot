package com.spot.trade.service;

import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradeEntity;
import com.spot.trade.entity.TradingPairEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class TradeEngineRouter implements TradeOrderService, TradeMatchingService, TradePositionService, TradeRiskService {
    private final TradeEngineFactory factory;

    public TradeEngineRouter(TradeEngineFactory factory) {
        this.factory = factory;
    }

    @Override
    public OrderEntity placeOrder(PlaceOrderCommand command) {
        return factory.orderService().placeOrder(command);
    }

    @Override
    public OrderEntity cancelOrder(CancelOrderCommand command) {
        return factory.orderService().cancelOrder(command);
    }

    @Override
    public List<OrderEntity> openOrders(UUID userId, int limit) {
        return factory.orderService().openOrders(userId, limit);
    }

    @Override
    public List<OrderEntity> orderHistory(UUID userId, int limit) {
        return factory.orderService().orderHistory(userId, limit);
    }

    @Override
    public List<TradeEntity> match(TradingPairEntity pair, OrderEntity taker) {
        return factory.matchingService().match(pair, taker);
    }

    @Override
    public long requiredQuoteForBuyLimit(long priceAtomic, long qtyAtomic, int feeBps) {
        return factory.matchingService().requiredQuoteForBuyLimit(priceAtomic, qtyAtomic, feeBps);
    }

    @Override
    public void reserveForPlacement(UUID userId, TradingPairEntity pair, com.spot.trade.model.OrderSide side,
            com.spot.trade.model.OrderType type, long qtyAtomic, long reserveQuote, String refId) {
        factory.positionService().reserveForPlacement(userId, pair, side, type, qtyAtomic, reserveQuote, refId);
    }

    @Override
    public long estimateMarketBuyReserve(UUID pairId, long qtyAtomic, int feeBps) {
        return factory.positionService().estimateMarketBuyReserve(pairId, qtyAtomic, feeBps);
    }

    @Override
    public void settleTrade(TradingPairEntity pair, OrderEntity maker, OrderEntity taker, long tradeQty, long quoteQty,
            long makerFee, long takerFee) {
        factory.positionService().settleTrade(pair, maker, taker, tradeQty, quoteQty, makerFee, takerFee);
    }

    @Override
    public void releaseBuyPriceImprovement(TradingPairEntity pair, OrderEntity buyOrder, long requiredQuote) {
        factory.positionService().releaseBuyPriceImprovement(pair, buyOrder, requiredQuote);
    }

    @Override
    public void releaseOnCancel(UUID userId, TradingPairEntity pair, OrderEntity order, long remainingQty) {
        factory.positionService().releaseOnCancel(userId, pair, order, remainingQty);
    }

    @Override
    public void finalizeMarketResidual(TradingPairEntity pair, OrderEntity order) {
        factory.positionService().finalizeMarketResidual(pair, order);
    }

    @Override
    public void releaseFilledResidualQuote(OrderEntity order, TradingPairEntity pair) {
        factory.positionService().releaseFilledResidualQuote(order, pair);
    }

    @Override
    public ValidatedPlaceOrder validatePlaceOrder(PlaceOrderCommand command) {
        return factory.riskService().validatePlaceOrder(command);
    }
}
