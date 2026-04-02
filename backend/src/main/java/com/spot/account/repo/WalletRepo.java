package com.spot.account.repo;

import com.spot.account.entity.WalletEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface WalletRepo extends JpaRepository<WalletEntity, UUID> {
    List<WalletEntity> findByUserId(UUID userId);
    Optional<WalletEntity> findByUserIdAndAssetId(UUID userId, UUID assetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WalletEntity> findWithLockByUserIdAndAssetId(UUID userId, UUID assetId);
}
