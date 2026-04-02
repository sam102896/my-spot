package com.spot.account.repo;

import com.spot.account.entity.OtpEntity;
import com.spot.account.model.OtpPurpose;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OtpRepo extends JpaRepository<OtpEntity, UUID> {
    @Query("""
            select o from OtpEntity o
            where o.userId = :userId
              and o.purpose = :purpose
              and o.consumedAt is null
              and o.expiresAt > :now
            order by o.createdAt desc
            """)
    Optional<OtpEntity> findLatestValid(@Param("userId") UUID userId, @Param("purpose") OtpPurpose purpose,
            @Param("now") Instant now);
}
