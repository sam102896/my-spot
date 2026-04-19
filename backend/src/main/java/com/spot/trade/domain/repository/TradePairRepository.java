package com.spot.trade.domain.repository;

import com.spot.trade.entity.TradingPairEntity;
import java.util.Optional;
import java.util.UUID;

public interface TradePairRepository {
    Optional<TradingPairEntity> findById(UUID pairId);

    Optional<TradingPairEntity> findBySymbol(String symbol);
}
