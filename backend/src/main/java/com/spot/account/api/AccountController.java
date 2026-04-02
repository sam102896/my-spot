package com.spot.account.api;

import com.spot.account.entity.AssetEntity;
import com.spot.account.entity.DepositAddressEntity;
import com.spot.account.entity.UserEntity;
import com.spot.account.model.DepositStatus;
import com.spot.account.repo.AssetRepo;
import com.spot.account.repo.DepositRepo;
import com.spot.account.repo.LedgerRepo;
import com.spot.account.repo.OperationLogRepo;
import com.spot.account.repo.UserRepo;
import com.spot.account.repo.WithdrawalRepo;
import com.spot.account.service.AuthService;
import com.spot.account.service.DepositService;
import com.spot.account.service.KycService;
import com.spot.account.service.WalletService;
import com.spot.account.service.WithdrawalService;
import com.spot.common.api.ApiException;
import com.spot.common.money.Atomic;
import com.spot.common.web.RequestContext;
import com.spot.security.Auth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final UserRepo userRepo;
    private final AssetRepo assetRepo;
    private final WalletService walletService;
    private final LedgerRepo ledgerRepo;
    private final DepositService depositService;
    private final DepositRepo depositRepo;
    private final WithdrawalService withdrawalService;
    private final WithdrawalRepo withdrawalRepo;
    private final OperationLogRepo logRepo;
    private final AuthService authService;
    private final KycService kycService;
    private final com.spot.account.repo.DeviceRepo deviceRepo;

    public AccountController(UserRepo userRepo, AssetRepo assetRepo, WalletService walletService, LedgerRepo ledgerRepo,
            DepositService depositService, DepositRepo depositRepo, WithdrawalService withdrawalService,
            WithdrawalRepo withdrawalRepo, OperationLogRepo logRepo, AuthService authService, KycService kycService,
            com.spot.account.repo.DeviceRepo deviceRepo) {
        this.userRepo = userRepo;
        this.assetRepo = assetRepo;
        this.walletService = walletService;
        this.ledgerRepo = ledgerRepo;
        this.depositService = depositService;
        this.depositRepo = depositRepo;
        this.withdrawalService = withdrawalService;
        this.withdrawalRepo = withdrawalRepo;
        this.logRepo = logRepo;
        this.authService = authService;
        this.kycService = kycService;
        this.deviceRepo = deviceRepo;
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        UUID userId = UUID.fromString(Auth.requireUserId());
        UserEntity u = userRepo.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", u.getId().toString());
        out.put("email", u.getEmail());
        out.put("phone", u.getPhone());
        out.put("name", u.getName());
        out.put("kycStatus", u.getKycStatus().name());
        out.put("status", u.getStatus().name());
        return out;
    }

    @PostMapping("/kyc")
    public Map<String, Object> kyc(@RequestBody KycReq req, HttpServletRequest http) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        UserEntity u = kycService.submitBasicKyc(userId, req.name, RequestContext.ip(http),
                RequestContext.deviceId(http));
        return Map.of("kycStatus", u.getKycStatus().name(), "name", u.getName());
    }

    @GetMapping("/wallets")
    public List<Map<String, Object>> wallets() {
        UUID userId = UUID.fromString(Auth.requireUserId());
        var ws = walletService.listWallets(userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (var w : ws) {
            AssetEntity a = assetRepo.findById(w.getAssetId()).orElse(null);
            out.add(Map.of("asset", a == null ? w.getAssetId().toString() : a.getSymbol(), "available",
                    w.getAvailable(), "frozen", w.getFrozen()));
        }
        return out;
    }

    @GetMapping("/ledger")
    public List<Map<String, Object>> ledger(@RequestParam(value = "asset", required = false) String asset,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        int size = Math.min(limit, 200);
        var items = asset == null || asset.isBlank()
                ? ledgerRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, size))
                : ledgerRepo.findByUserIdAndAssetIdOrderByCreatedAtDesc(userId, assetId(asset),
                        PageRequest.of(0, size));
        List<Map<String, Object>> out = new ArrayList<>();
        for (var e : items) {
            AssetEntity a = assetRepo.findById(e.getAssetId()).orElse(null);
            out.add(Map.of("asset", a == null ? e.getAssetId().toString() : a.getSymbol(), "type", e.getType().name(),
                    "amount", e.getAmount(), "availableAfter", e.getAvailableAfter(), "frozenAfter", e.getFrozenAfter(),
                    "refType", e.getRefType(), "refId", e.getRefId(), "createdAt", e.getCreatedAt().toString()));
        }
        return out;
    }

    @GetMapping("/deposit/address")
    public Map<String, Object> depositAddress(@RequestParam("asset") @NotBlank String asset) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        DepositAddressEntity a = depositService.getOrCreateAddress(userId, asset.toUpperCase());
        return Map.of("asset", asset.toUpperCase(), "address", a.getAddress());
    }

    @GetMapping("/deposits")
    public List<Map<String, Object>> deposits(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        var ds = depositRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(limit, 200)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (var d : ds) {
            AssetEntity a = assetRepo.findById(d.getAssetId()).orElse(null);
            out.add(Map.of("id", d.getId().toString(), "asset", a == null ? d.getAssetId().toString() : a.getSymbol(),
                    "amount", d.getAmount(), "txId", d.getTxId(), "status", d.getStatus().name(), "createdAt",
                    d.getCreatedAt().toString()));
        }
        return out;
    }

    @PostMapping("/withdraw")
    public Map<String, Object> withdraw(@RequestBody WithdrawReq req, HttpServletRequest http) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        long amount = Atomic.parse(req.amount, Atomic.DEFAULT_DECIMALS);
        var w = withdrawalService.requestWithdraw(userId, req.asset.toUpperCase(), req.address, amount,
                req.fundPassword, RequestContext.ip(http), RequestContext.deviceId(http));
        return Map.of("id", w.getId().toString(), "status", w.getStatus().name(), "fee", w.getFee());
    }

    @PostMapping("/withdraw/{id}/cancel")
    public Map<String, Object> cancelWithdraw(@PathVariable("id") String id, HttpServletRequest http) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        var w = withdrawalService.cancel(userId, UUID.fromString(id), RequestContext.ip(http),
                RequestContext.deviceId(http));
        return Map.of("id", w.getId().toString(), "status", w.getStatus().name());
    }

    @GetMapping("/withdrawals")
    public List<Map<String, Object>> withdrawals(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        var ws = withdrawalRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(limit, 200)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (var w : ws) {
            AssetEntity a = assetRepo.findById(w.getAssetId()).orElse(null);
            out.add(Map.of("id", w.getId().toString(), "asset", a == null ? w.getAssetId().toString() : a.getSymbol(),
                    "amount", w.getAmount(), "fee", w.getFee(), "address", w.getAddress(), "status",
                    w.getStatus().name(), "createdAt", w.getCreatedAt().toString(), "updatedAt",
                    w.getUpdatedAt().toString()));
        }
        return out;
    }

    @PostMapping("/password/login")
    public Map<String, Object> changeLoginPassword(@RequestBody ChangeLoginPasswordReq req, HttpServletRequest http) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        authService.changeLoginPassword(userId, req.oldPassword, req.newPassword, RequestContext.ip(http),
                RequestContext.deviceId(http));
        return Map.of("ok", true);
    }

    @PostMapping("/password/fund")
    public Map<String, Object> setFundPassword(@RequestBody SetFundPasswordReq req, HttpServletRequest http) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        authService.setOrChangeFundPassword(userId, req.loginPassword, req.newFundPassword, RequestContext.ip(http),
                RequestContext.deviceId(http));
        return Map.of("ok", true);
    }

    @PostMapping("/devices/bind")
    public Map<String, Object> bindDevice(@RequestBody BindDeviceReq req, HttpServletRequest http) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        String deviceId = RequestContext.deviceId(http);
        var d = deviceRepo.findByUserIdAndDeviceId(userId, deviceId).orElseGet(() -> {
            var e = new com.spot.account.entity.DeviceEntity();
            e.setUserId(userId);
            e.setDeviceId(deviceId);
            e.setLabel(req.label);
            return deviceRepo.save(e);
        });
        d.setLastSeenAt(java.time.Instant.now());
        if (req.label != null && !req.label.isBlank()) {
            d.setLabel(req.label.trim());
        }
        deviceRepo.save(d);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deviceId", d.getDeviceId());
        out.put("label", d.getLabel());
        return out;
    }

    @GetMapping("/logs")
    public List<Map<String, Object>> logs(@RequestParam(value = "limit", defaultValue = "50") int limit) {
        UUID userId = UUID.fromString(Auth.requireUserId());
        var logs = logRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(0, Math.min(limit, 200)));
        List<Map<String, Object>> out = new ArrayList<>();
        for (var l : logs) {
            out.add(Map.of("type", l.getType(), "ip", l.getIp(), "deviceId", l.getDeviceId(), "detail",
                    l.getDetailJson(), "createdAt", l.getCreatedAt().toString()));
        }
        return out;
    }

    private UUID assetId(String symbol) {
        return assetRepo.findBySymbol(symbol.toUpperCase())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "币种不存在")).getId();
    }

    public record KycReq(@NotBlank String name) {
    }

    public record WithdrawReq(@NotBlank String asset, @NotBlank String address, @NotBlank String amount,
            @NotBlank String fundPassword) {
    }

    public record ChangeLoginPasswordReq(@NotBlank String oldPassword, @NotBlank String newPassword) {
    }

    public record SetFundPasswordReq(@NotBlank String loginPassword, @NotBlank String newFundPassword) {
    }

    public record BindDeviceReq(String label) {
    }
}
