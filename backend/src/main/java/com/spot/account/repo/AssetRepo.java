package com.spot.account.repo;

import com.spot.account.entity.AssetEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepo extends JpaRepository<AssetEntity, UUID> {
    Optional<AssetEntity> findBySymbol(String symbol);
}
