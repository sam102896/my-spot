package com.spot.account.repo;

import com.spot.account.entity.DeviceEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepo extends JpaRepository<DeviceEntity, UUID> {
    List<DeviceEntity> findByUserIdOrderByLastSeenAtDesc(UUID userId);
    Optional<DeviceEntity> findByUserIdAndDeviceId(UUID userId, String deviceId);
}
