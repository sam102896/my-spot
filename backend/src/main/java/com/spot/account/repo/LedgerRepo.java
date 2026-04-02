package com.spot.account.repo;

import com.spot.account.entity.LedgerEntryEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerRepo extends JpaRepository<LedgerEntryEntity, UUID> {
    List<LedgerEntryEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    List<LedgerEntryEntity> findByUserIdAndAssetIdOrderByCreatedAtDesc(UUID userId, UUID assetId, Pageable pageable);

    boolean existsByRefTypeAndRefId(String refType, String refId);
}
