package com.spot.trade.service;

import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradingPairEntity;
import com.spot.trade.model.OrderSide;
import com.spot.trade.model.OrderType;
import java.util.UUID;

public interface TradePositionService {
    void reserveForPlacement(UUID userId, TradingPairEntity pair, OrderSide side, OrderType type, long qtyAtomic,
            long reserveQuote, String refId);

    long estimateMarketBuyReserve(UUID pairId, long qtyAtomic, int feeBps);

    void settleTrade(TradingPairEntity pair, OrderEntity maker, OrderEntity taker, long tradeQty, long quoteQty,
            long makerFee, long takerFee);

    void releaseBuyPriceImprovement(TradingPairEntity pair, OrderEntity buyOrder, long requiredQuote);

    void releaseOnCancel(UUID userId, TradingPairEntity pair, OrderEntity order, long remainingQty);

    void finalizeMarketResidual(TradingPairEntity pair, OrderEntity order);

    void releaseFilledResidualQuote(OrderEntity order, TradingPairEntity pair);
}
