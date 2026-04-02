package com.spot.account.api;

import com.spot.account.repo.UserRepo;
import com.spot.account.service.DepositService;
import com.spot.common.api.ApiException;
import com.spot.common.money.Atomic;
import com.spot.common.web.DevAdminGuard;
import com.spot.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/admin")
public class DevAdminController {
    private final DevAdminGuard guard;
    private final UserRepo userRepo;
    private final DepositService depositService;

    public DevAdminController(DevAdminGuard guard, UserRepo userRepo, DepositService depositService) {
        this.guard = guard;
        this.userRepo = userRepo;
        this.depositService = depositService;
    }

    @PostMapping("/deposits/simulate")
    public Map<String, Object> simulateDeposit(@RequestBody SimDepositReq req, HttpServletRequest http) {
        guard.require(http);
        var u = userRepo.findByIdentifier(req.identifier.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "账号不存在"));
        long amount = Atomic.parse(req.amount, Atomic.DEFAULT_DECIMALS);
        var d = depositService.simulateIncomingDeposit(u.getId(), req.asset.toUpperCase(), amount, req.txId,
                RequestContext.ip(http), RequestContext.deviceId(http));
        return Map.of("id", d.getId().toString(), "txId", d.getTxId(), "status", d.getStatus().name());
    }

    public record SimDepositReq(@NotBlank String identifier, @NotBlank String asset, @NotBlank String amount,
            String txId) {
    }
}
