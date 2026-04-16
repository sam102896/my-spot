package com.spot.trade.service;

import com.spot.trade.entity.OrderEntity;
import com.spot.trade.entity.TradeEntity;
import com.spot.trade.entity.TradingPairEntity;
import java.util.List;

public interface TradeMatchingService {
    List<TradeEntity> match(TradingPairEntity pair, OrderEntity taker);

    long requiredQuoteForBuyLimit(long priceAtomic, long qtyAtomic, int feeBps);
}
