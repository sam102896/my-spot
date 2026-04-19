package com.spot.trade.service;

import com.spot.account.entity.UserEntity;
import com.spot.trade.entity.OrderIntentEntity;
import com.spot.trade.entity.TradingPairEntity;

public interface TradeRiskService {
    ValidatedPlaceOrder validatePlaceOrder(TradeOrderService.PlaceOrderCommand command);

    record ValidatedPlaceOrder(UserEntity user, TradingPairEntity pair, Long priceAtomic, long qtyAtomic,
            OrderIntentEntity intent) {
        public static ValidatedPlaceOrder of(UserEntity user, TradingPairEntity pair, Long priceAtomic, long qtyAtomic,
                OrderIntentEntity intent) {
            return new ValidatedPlaceOrder(user, pair, priceAtomic, qtyAtomic, intent);
        }
    }
}
