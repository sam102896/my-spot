package com.spot.account.repo;

import com.spot.account.entity.DepositAddressEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepositAddressRepo extends JpaRepository<DepositAddressEntity, UUID> {
    Optional<DepositAddressEntity> findByUserIdAndAssetId(UUID userId, UUID assetId);
    Optional<DepositAddressEntity> findByAddress(String address);
}
