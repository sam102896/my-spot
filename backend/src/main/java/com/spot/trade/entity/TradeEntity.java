package com.spot.trade.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trades", indexes = {@Index(name = "ix_trade_pair_time", columnList = "pairId,createdAt"),
        @Index(name = "ix_trade_maker_time", columnList = "makerUserId,createdAt"),
        @Index(name = "ix_trade_taker_time", columnList = "takerUserId,createdAt")})
public class TradeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID pairId;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false)
    private long qty;

    @Column(nullable = false)
    private long quoteQty;

    @Column(nullable = false)
    private UUID makerOrderId;

    @Column(nullable = false)
    private UUID takerOrderId;

    @Column(nullable = false)
    private UUID makerUserId;

    @Column(nullable = false)
    private UUID takerUserId;

    @Column(nullable = false)
    private long makerFee;

    @Column(nullable = false)
    private long takerFee;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPairId() {
        return pairId;
    }

    public void setPairId(UUID pairId) {
        this.pairId = pairId;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public long getQty() {
        return qty;
    }

    public void setQty(long qty) {
        this.qty = qty;
    }

    public long getQuoteQty() {
        return quoteQty;
    }

    public void setQuoteQty(long quoteQty) {
        this.quoteQty = quoteQty;
    }

    public UUID getMakerOrderId() {
        return makerOrderId;
    }

    public void setMakerOrderId(UUID makerOrderId) {
        this.makerOrderId = makerOrderId;
    }

    public UUID getTakerOrderId() {
        return takerOrderId;
    }

    public void setTakerOrderId(UUID takerOrderId) {
        this.takerOrderId = takerOrderId;
    }

    public UUID getMakerUserId() {
        return makerUserId;
    }

    public void setMakerUserId(UUID makerUserId) {
        this.makerUserId = makerUserId;
    }

    public UUID getTakerUserId() {
        return takerUserId;
    }

    public void setTakerUserId(UUID takerUserId) {
        this.takerUserId = takerUserId;
    }

    public long getMakerFee() {
        return makerFee;
    }

    public void setMakerFee(long makerFee) {
        this.makerFee = makerFee;
    }

    public long getTakerFee() {
        return takerFee;
    }

    public void setTakerFee(long takerFee) {
        this.takerFee = takerFee;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
