package com.spot.trade.repo;

import com.spot.trade.entity.OrderIntentEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderIntentRepo extends JpaRepository<OrderIntentEntity, UUID> {
    Optional<OrderIntentEntity> findByUserIdAndIdemKey(UUID userId, String idemKey);
}
