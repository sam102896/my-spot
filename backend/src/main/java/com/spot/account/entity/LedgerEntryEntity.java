package com.spot.account.entity;

import com.spot.account.model.LedgerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries", indexes = {
        @Index(name = "ix_ledger_user_asset_time", columnList = "userId,assetId,createdAt")})
public class LedgerEntryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID assetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LedgerType type;

    @Column(nullable = false)
    private long amount;

    @Column(nullable = false)
    private long availableAfter;

    @Column(nullable = false)
    private long frozenAfter;

    @Column(nullable = false, length = 30)
    private String refType;

    @Column(nullable = false, length = 60)
    private String refId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

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

    public LedgerType getType() {
        return type;
    }

    public void setType(LedgerType type) {
        this.type = type;
    }

    public long getAmount() {
        return amount;
    }

    public void setAmount(long amount) {
        this.amount = amount;
    }

    public long getAvailableAfter() {
        return availableAfter;
    }

    public void setAvailableAfter(long availableAfter) {
        this.availableAfter = availableAfter;
    }

    public long getFrozenAfter() {
        return frozenAfter;
    }

    public void setFrozenAfter(long frozenAfter) {
        this.frozenAfter = frozenAfter;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
