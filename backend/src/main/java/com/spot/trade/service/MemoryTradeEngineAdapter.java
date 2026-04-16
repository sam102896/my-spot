package com.spot.trade.service;

import com.spot.common.api.ApiException;
import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradeEntity;
import com.spot.trade.entity.TradingPairEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class MemoryTradeEngineAdapter
        implements TradeOrderService, TradeMatchingService, TradePositionService, TradeRiskService, TradeEngineAware {
    @Override
    public TradeEngineType engineType() {
        return TradeEngineType.MEMORY;
    }

    @Override
    public OrderEntity placeOrder(PlaceOrderCommand command) {
        throw unsupported();
    }

    @Override
    public OrderEntity cancelOrder(CancelOrderCommand command) {
        throw unsupported();
    }

    @Override
    public List<OrderEntity> openOrders(UUID userId, int limit) {
        throw unsupported();
    }

    @Override
    public List<OrderEntity> orderHistory(UUID userId, int limit) {
        throw unsupported();
    }

    @Override
    public List<TradeEntity> match(TradingPairEntity pair, OrderEntity taker) {
        throw unsupported();
    }

    @Override
    public long requiredQuoteForBuyLimit(long priceAtomic, long qtyAtomic, int feeBps) {
        throw unsupported();
    }

    @Override
    public void reserveForPlacement(UUID userId, TradingPairEntity pair, com.spot.trade.model.OrderSide side,
            com.spot.trade.model.OrderType type, long qtyAtomic, long reserveQuote, String refId) {
        throw unsupported();
    }

    @Override
    public long estimateMarketBuyReserve(UUID pairId, long qtyAtomic, int feeBps) {
        throw unsupported();
    }

    @Override
    public void settleTrade(TradingPairEntity pair, OrderEntity maker, OrderEntity taker, long tradeQty, long quoteQty,
            long makerFee, long takerFee) {
        throw unsupported();
    }

    @Override
    public void releaseBuyPriceImprovement(TradingPairEntity pair, OrderEntity buyOrder, long requiredQuote) {
        throw unsupported();
    }

    @Override
    public void releaseOnCancel(UUID userId, TradingPairEntity pair, OrderEntity order, long remainingQty) {
        throw unsupported();
    }

    @Override
    public void finalizeMarketResidual(TradingPairEntity pair, OrderEntity order) {
        throw unsupported();
    }

    @Override
    public void releaseFilledResidualQuote(OrderEntity order, TradingPairEntity pair) {
        throw unsupported();
    }

    @Override
    public ValidatedPlaceOrder validatePlaceOrder(PlaceOrderCommand command) {
        throw unsupported();
    }

    private ApiException unsupported() {
        return new ApiException(HttpStatus.NOT_IMPLEMENTED, "ENGINE_NOT_IMPLEMENTED",
                "内存撮合引擎骨架已预留，具体实现待接入订单簿内存化、无锁队列与状态复制");
    }
}
