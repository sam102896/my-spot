package com.spot.account.repo;

import com.spot.account.entity.DepositEntity;
import com.spot.account.model.DepositStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositRepo extends JpaRepository<DepositEntity, UUID> {
    List<DepositEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    List<DepositEntity> findByStatusOrderByCreatedAtAsc(DepositStatus status, Pageable pageable);
}
