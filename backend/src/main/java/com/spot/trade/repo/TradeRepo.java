package com.spot.trade.repo;

import com.spot.trade.entity.TradeEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TradeRepo extends JpaRepository<TradeEntity, UUID> {
    List<TradeEntity> findByPairIdOrderByCreatedAtDesc(UUID pairId, Pageable pageable);
    List<TradeEntity> findByMakerUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    List<TradeEntity> findByTakerUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
