package com.spot.account.service;

import com.spot.account.entity.AssetEntity;
import com.spot.account.entity.DepositAddressEntity;
import com.spot.account.entity.DepositEntity;
import com.spot.account.model.DepositStatus;
import com.spot.account.model.LedgerType;
import com.spot.account.repo.AssetRepo;
import com.spot.account.repo.DepositAddressRepo;
import com.spot.account.repo.DepositRepo;
import com.spot.common.api.ApiException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DepositService {
    private final AssetRepo assetRepo;
    private final DepositAddressRepo addressRepo;
    private final DepositRepo depositRepo;
    private final WalletService walletService;
    private final OperationLogService logService;

    public DepositService(AssetRepo assetRepo, DepositAddressRepo addressRepo, DepositRepo depositRepo,
            WalletService walletService, OperationLogService logService) {
        this.assetRepo = assetRepo;
        this.addressRepo = addressRepo;
        this.depositRepo = depositRepo;
        this.walletService = walletService;
        this.logService = logService;
    }

    @Transactional
    public DepositAddressEntity getOrCreateAddress(UUID userId, String assetSymbol) {
        AssetEntity asset = assetRepo.findBySymbol(assetSymbol)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "币种不存在"));
        return addressRepo.findByUserIdAndAssetId(userId, asset.getId()).orElseGet(() -> {
            DepositAddressEntity a = new DepositAddressEntity();
            a.setUserId(userId);
            a.setAssetId(asset.getId());
            a.setAddress("ADDR-" + assetSymbol + "-" + userId.toString().substring(0, 8));
            return addressRepo.save(a);
        });
    }

    @Transactional
    public DepositEntity simulateIncomingDeposit(UUID userId, String assetSymbol, long amountAtomic, String txId,
            String ip, String deviceId) {
        if (amountAtomic <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "金额必须大于0");
        }
        AssetEntity asset = assetRepo.findBySymbol(assetSymbol)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "币种不存在"));
        DepositEntity d = new DepositEntity();
        d.setUserId(userId);
        d.setAssetId(asset.getId());
        d.setAmount(amountAtomic);
        d.setTxId(txId == null || txId.isBlank() ? "TX-" + UUID.randomUUID() : txId.trim());
        d.setStatus(DepositStatus.PENDING);
        d.setCreatedAt(Instant.now());
        d = depositRepo.save(d);
        logService.log(userId, "DEPOSIT_INCOMING", ip, deviceId,
                Map.of("asset", assetSymbol, "amount", amountAtomic, "txId", d.getTxId()));
        return d;
    }

    @Transactional
    public void confirmDeposit(DepositEntity d) {
        if (d.getStatus() != DepositStatus.PENDING) {
            return;
        }
        d.setStatus(DepositStatus.CONFIRMED);
        d.setConfirmedAt(Instant.now());
        depositRepo.save(d);
        walletService.addAvailable(d.getUserId(), d.getAssetId(), d.getAmount(), LedgerType.DEPOSIT, "DEPOSIT",
                d.getId().toString());
    }
}
