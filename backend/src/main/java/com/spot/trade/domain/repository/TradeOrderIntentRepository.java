package com.spot.trade.domain.repository;

import com.spot.trade.entity.OrderIntentEntity;
import java.util.Optional;
import java.util.UUID;

public interface TradeOrderIntentRepository {
    Optional<OrderIntentEntity> findByUserIdAndIdemKey(UUID userId, String idemKey);

    OrderIntentEntity save(OrderIntentEntity intent);
}
