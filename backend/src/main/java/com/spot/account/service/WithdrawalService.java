package com.spot.account.service;

import com.spot.account.entity.AssetEntity;
import com.spot.account.entity.UserEntity;
import com.spot.account.entity.WithdrawalEntity;
import com.spot.account.model.KycStatus;
import com.spot.account.model.LedgerType;
import com.spot.account.model.WithdrawalStatus;
import com.spot.account.repo.AssetRepo;
import com.spot.account.repo.UserRepo;
import com.spot.account.repo.WithdrawalRepo;
import com.spot.common.api.ApiException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawalService {
    private final WithdrawalRepo withdrawalRepo;
    private final AssetRepo assetRepo;
    private final UserRepo userRepo;
    private final WalletService walletService;
    private final AuthService authService;
    private final OperationLogService logService;

    public WithdrawalService(WithdrawalRepo withdrawalRepo, AssetRepo assetRepo, UserRepo userRepo,
            WalletService walletService, AuthService authService, OperationLogService logService) {
        this.withdrawalRepo = withdrawalRepo;
        this.assetRepo = assetRepo;
        this.userRepo = userRepo;
        this.walletService = walletService;
        this.authService = authService;
        this.logService = logService;
    }

    @Transactional
    public WithdrawalEntity requestWithdraw(UUID userId, String assetSymbol, String address, long amountAtomic,
            String fundPassword, String ip, String deviceId) {
        if (amountAtomic <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT", "金额必须大于0");
        }
        if (address == null || address.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADDRESS_REQUIRED", "提现地址不能为空");
        }
        UserEntity u = userRepo.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        if (u.getKycStatus() != KycStatus.VERIFIED) {
            throw new ApiException(HttpStatus.FORBIDDEN, "KYC_REQUIRED", "完成基础KYC后才能提现");
        }
        if (!authService.verifyFundPassword(userId, fundPassword)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "FUND_PASSWORD_INVALID", "资金密码错误");
        }
        AssetEntity asset = assetRepo.findBySymbol(assetSymbol)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "币种不存在"));
        long fee = withdrawFeeAtomic(assetSymbol);
        long total = Math.addExact(amountAtomic, fee);
        WithdrawalEntity w = new WithdrawalEntity();
        w.setUserId(userId);
        w.setAssetId(asset.getId());
        w.setAddress(address.trim());
        w.setAmount(amountAtomic);
        w.setFee(fee);
        w.setStatus(WithdrawalStatus.PENDING);
        w.setCreatedAt(Instant.now());
        w.setUpdatedAt(Instant.now());
        w = withdrawalRepo.save(w);

        walletService.freezeAvailable(userId, asset.getId(), total, "WITHDRAWAL", w.getId().toString());
        logService.log(userId, "WITHDRAW_REQUEST", ip, deviceId,
                Map.of("asset", assetSymbol, "amount", amountAtomic, "fee", fee, "id", w.getId().toString()));
        return w;
    }

    @Transactional
    public WithdrawalEntity cancel(UUID userId, UUID withdrawalId, String ip, String deviceId) {
        WithdrawalEntity w = withdrawalRepo.findById(withdrawalId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "WITHDRAWAL_NOT_FOUND", "提现不存在"));
        if (!w.getUserId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权限");
        }
        if (w.getStatus() != WithdrawalStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "WITHDRAWAL_NOT_CANCELABLE", "该状态无法撤销");
        }
        w.setStatus(WithdrawalStatus.CANCELED);
        w.setUpdatedAt(Instant.now());
        withdrawalRepo.save(w);
        long total = Math.addExact(w.getAmount(), w.getFee());
        walletService.unfreezeToAvailable(userId, w.getAssetId(), total, "WITHDRAWAL", w.getId().toString());
        logService.log(userId, "WITHDRAW_CANCELED", ip, deviceId, Map.of("id", w.getId().toString()));
        return w;
    }

    @Transactional
    public void progressToProcessing(WithdrawalEntity w) {
        if (w.getStatus() != WithdrawalStatus.PENDING) {
            return;
        }
        w.setStatus(WithdrawalStatus.PROCESSING);
        w.setUpdatedAt(Instant.now());
        withdrawalRepo.save(w);
    }

    @Transactional
    public void finishDone(WithdrawalEntity w) {
        if (w.getStatus() != WithdrawalStatus.PROCESSING) {
            return;
        }
        w.setStatus(WithdrawalStatus.DONE);
        w.setUpdatedAt(Instant.now());
        withdrawalRepo.save(w);
        walletService.spendFrozen(w.getUserId(), w.getAssetId(), w.getAmount(), LedgerType.WITHDRAW, "WITHDRAWAL",
                w.getId().toString());
        if (w.getFee() > 0) {
            walletService.spendFrozen(w.getUserId(), w.getAssetId(), w.getFee(), LedgerType.FEE, "WITHDRAWAL",
                    w.getId().toString());
        }
    }

    private long withdrawFeeAtomic(String assetSymbol) {
        if ("USDT".equalsIgnoreCase(assetSymbol)) {
            return 1000000L;
        }
        return 0L;
    }
}
