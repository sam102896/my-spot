package com.spot.account.service;

import com.spot.account.entity.LedgerEntryEntity;
import com.spot.account.entity.WalletEntity;
import com.spot.account.model.LedgerType;
import com.spot.account.repo.LedgerRepo;
import com.spot.account.repo.WalletRepo;
import com.spot.common.api.ApiException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WalletService {
    private final WalletRepo walletRepo;
    private final LedgerRepo ledgerRepo;

    public WalletService(WalletRepo walletRepo, LedgerRepo ledgerRepo) {
        this.walletRepo = walletRepo;
        this.ledgerRepo = ledgerRepo;
    }

    @Transactional
    public WalletEntity ensureWallet(UUID userId, UUID assetId) {
        return walletRepo.findByUserIdAndAssetId(userId, assetId).orElseGet(() -> {
            WalletEntity w = new WalletEntity();
            w.setUserId(userId);
            w.setAssetId(assetId);
            w.setAvailable(0);
            w.setFrozen(0);
            w.setUpdatedAt(Instant.now());
            return walletRepo.save(w);
        });
    }

    public List<WalletEntity> listWallets(UUID userId) {
        return walletRepo.findByUserId(userId);
    }

    @Transactional
    public WalletEntity addAvailable(UUID userId, UUID assetId, long amount, LedgerType type, String refType,
            String refId) {
        if (amount <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "金额必须大于0");
        }
        WalletEntity w = walletRepo.findWithLockByUserIdAndAssetId(userId, assetId)
                .orElseGet(() -> ensureWallet(userId, assetId));
        w.setAvailable(Math.addExact(w.getAvailable(), amount));
        w.setUpdatedAt(Instant.now());
        walletRepo.save(w);
        writeLedger(userId, assetId, type, amount, w.getAvailable(), w.getFrozen(), refType, refId);
        return w;
    }

    @Transactional
    public WalletEntity freezeAvailable(UUID userId, UUID assetId, long amount, String refType, String refId) {
        if (amount <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "金额必须大于0");
        }
        WalletEntity w = walletRepo.findWithLockByUserIdAndAssetId(userId, assetId)
                .orElseGet(() -> ensureWallet(userId, assetId));
        if (w.getAvailable() < amount) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_BALANCE", "余额不足");
        }
        w.setAvailable(w.getAvailable() - amount);
        w.setFrozen(Math.addExact(w.getFrozen(), amount));
        w.setUpdatedAt(Instant.now());
        walletRepo.save(w);
        writeLedger(userId, assetId, LedgerType.FREEZE, -amount, w.getAvailable(), w.getFrozen(), refType, refId);
        return w;
    }

    @Transactional
    public WalletEntity unfreezeToAvailable(UUID userId, UUID assetId, long amount, String refType, String refId) {
        if (amount <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "金额必须大于0");
        }
        WalletEntity w = walletRepo.findWithLockByUserIdAndAssetId(userId, assetId)
                .orElseGet(() -> ensureWallet(userId, assetId));
        if (w.getFrozen() < amount) {
            throw new ApiException(HttpStatus.CONFLICT, "FROZEN_MISMATCH", "冻结余额不足，可能存在并发成交/撤单");
        }
        w.setFrozen(w.getFrozen() - amount);
        w.setAvailable(Math.addExact(w.getAvailable(), amount));
        w.setUpdatedAt(Instant.now());
        walletRepo.save(w);
        writeLedger(userId, assetId, LedgerType.UNFREEZE, amount, w.getAvailable(), w.getFrozen(), refType, refId);
        return w;
    }

    @Transactional
    public WalletEntity spendFrozen(UUID userId, UUID assetId, long amount, LedgerType type, String refType,
            String refId) {
        if (amount <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "金额必须大于0");
        }
        WalletEntity w = walletRepo.findWithLockByUserIdAndAssetId(userId, assetId)
                .orElseGet(() -> ensureWallet(userId, assetId));
        if (w.getFrozen() < amount) {
            throw new ApiException(HttpStatus.CONFLICT, "FROZEN_MISMATCH", "冻结余额不足，可能存在并发成交/撤单");
        }
        w.setFrozen(w.getFrozen() - amount);
        w.setUpdatedAt(Instant.now());
        walletRepo.save(w);
        writeLedger(userId, assetId, type, -amount, w.getAvailable(), w.getFrozen(), refType, refId);
        return w;
    }

    @Transactional
    public WalletEntity spendAvailable(UUID userId, UUID assetId, long amount, LedgerType type, String refType,
            String refId) {
        if (amount <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "金额必须大于0");
        }
        WalletEntity w = walletRepo.findWithLockByUserIdAndAssetId(userId, assetId)
                .orElseGet(() -> ensureWallet(userId, assetId));
        if (w.getAvailable() < amount) {
            throw new ApiException(HttpStatus.CONFLICT, "INSUFFICIENT_AVAILABLE", "可用余额不足");
        }
        w.setAvailable(w.getAvailable() - amount);
        w.setUpdatedAt(Instant.now());
        walletRepo.save(w);
        writeLedger(userId, assetId, type, -amount, w.getAvailable(), w.getFrozen(), refType, refId);
        return w;
    }

    private void writeLedger(UUID userId, UUID assetId, LedgerType type, long amount, long availableAfter,
            long frozenAfter, String refType, String refId) {
        LedgerEntryEntity e = new LedgerEntryEntity();
        e.setUserId(userId);
        e.setAssetId(assetId);
        e.setType(type);
        e.setAmount(amount);
        e.setAvailableAfter(availableAfter);
        e.setFrozenAfter(frozenAfter);
        e.setRefType(refType);
        e.setRefId(refId);
        e.setCreatedAt(Instant.now());
        ledgerRepo.save(e);
    }
}
