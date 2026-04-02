package com.spot.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallets", uniqueConstraints = @UniqueConstraint(name = "uk_wallet_user_asset", columnNames = {"userId",
        "assetId"}), indexes = {@Index(name = "ix_wallet_user", columnList = "userId"),
                @Index(name = "ix_wallet_asset", columnList = "assetId")})
public class WalletEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID assetId;

    @Column(nullable = false)
    private long available = 0;

    @Column(nullable = false)
    private long frozen = 0;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    private long version;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public void setAssetId(UUID assetId) {
        this.assetId = assetId;
    }

    public long getAvailable() {
        return available;
    }

    public void setAvailable(long available) {
        this.available = available;
    }

    public long getFrozen() {
        return frozen;
    }

    public void setFrozen(long frozen) {
        this.frozen = frozen;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(long version) {
        this.version = version;
    }
}
