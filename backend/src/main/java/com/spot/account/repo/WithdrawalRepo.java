package com.spot.account.repo;

import com.spot.account.entity.WithdrawalEntity;
import com.spot.account.model.WithdrawalStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalRepo extends JpaRepository<WithdrawalEntity, UUID> {
    List<WithdrawalEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    List<WithdrawalEntity> findByStatusOrderByUpdatedAtAsc(WithdrawalStatus status, Pageable pageable);
}
