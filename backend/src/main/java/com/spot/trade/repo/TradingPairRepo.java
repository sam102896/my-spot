package com.spot.trade.repo;

import com.spot.trade.entity.TradingPairEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradingPairRepo extends JpaRepository<TradingPairEntity, UUID> {
    Optional<TradingPairEntity> findBySymbol(String symbol);
}
