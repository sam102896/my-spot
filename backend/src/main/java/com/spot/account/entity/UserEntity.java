package com.spot.account.entity;

import com.spot.account.model.KycStatus;
import com.spot.account.model.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {@UniqueConstraint(name = "uk_user_email", columnNames = {"email"}),
        @UniqueConstraint(name = "uk_user_phone", columnNames = {"phone"})})
public class UserEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(length = 120)
    private String email;

    @Column(length = 40)
    private String phone;

    @Column(nullable = false, length = 200)
    private String passwordHash;

    @Column(length = 200)
    private String fundPasswordHash;

    @Column(length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private KycStatus kycStatus = KycStatus.NONE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant lastLoginAt;

    @Column(length = 80)
    private String lastLoginIp;

    @Column(length = 80)
    private String lastLoginDeviceId;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFundPasswordHash() {
        return fundPasswordHash;
    }

    public void setFundPasswordHash(String fundPasswordHash) {
        this.fundPasswordHash = fundPasswordHash;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public void setKycStatus(KycStatus kycStatus) {
        this.kycStatus = kycStatus;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public String getLastLoginIp() {
        return lastLoginIp;
    }

    public void setLastLoginIp(String lastLoginIp) {
        this.lastLoginIp = lastLoginIp;
    }

    public String getLastLoginDeviceId() {
        return lastLoginDeviceId;
    }

    public void setLastLoginDeviceId(String lastLoginDeviceId) {
        this.lastLoginDeviceId = lastLoginDeviceId;
    }
}
