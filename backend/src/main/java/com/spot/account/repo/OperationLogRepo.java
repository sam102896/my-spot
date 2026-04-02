package com.spot.account.repo;

import com.spot.account.entity.OperationLogEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationLogRepo extends JpaRepository<OperationLogEntity, UUID> {
    List<OperationLogEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
