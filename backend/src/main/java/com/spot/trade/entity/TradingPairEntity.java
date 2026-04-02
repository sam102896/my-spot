package com.spot.trade.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trading_pairs", uniqueConstraints = @UniqueConstraint(name = "uk_pair_symbol", columnNames = {"symbol"}))
public class TradingPairEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String symbol;

    @Column(nullable = false)
    private UUID baseAssetId;

    @Column(nullable = false)
    private UUID quoteAssetId;

    @Column(nullable = false)
    private long minQty;

    @Column(nullable = false)
    private long minNotional;

    @Column(nullable = false)
    private int priceDecimals = 2;

    @Column(nullable = false)
    private int qtyDecimals = 6;

    @Column(nullable = false)
    private int feeBps = 10;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public UUID getBaseAssetId() {
        return baseAssetId;
    }

    public void setBaseAssetId(UUID baseAssetId) {
        this.baseAssetId = baseAssetId;
    }

    public UUID getQuoteAssetId() {
        return quoteAssetId;
    }

    public void setQuoteAssetId(UUID quoteAssetId) {
        this.quoteAssetId = quoteAssetId;
    }

    public long getMinQty() {
        return minQty;
    }

    public void setMinQty(long minQty) {
        this.minQty = minQty;
    }

    public long getMinNotional() {
        return minNotional;
    }

    public void setMinNotional(long minNotional) {
        this.minNotional = minNotional;
    }

    public int getPriceDecimals() {
        return priceDecimals;
    }

    public void setPriceDecimals(int priceDecimals) {
        this.priceDecimals = priceDecimals;
    }

    public int getQtyDecimals() {
        return qtyDecimals;
    }

    public void setQtyDecimals(int qtyDecimals) {
        this.qtyDecimals = qtyDecimals;
    }

    public int getFeeBps() {
        return feeBps;
    }

    public void setFeeBps(int feeBps) {
        this.feeBps = feeBps;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
