package com.spot.account.service;

import com.spot.account.entity.UserEntity;
import com.spot.account.model.KycStatus;
import com.spot.account.repo.UserRepo;
import com.spot.common.api.ApiException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KycService {
    private final UserRepo userRepo;
    private final OperationLogService logService;

    public KycService(UserRepo userRepo, OperationLogService logService) {
        this.userRepo = userRepo;
        this.logService = logService;
    }

    @Transactional
    public UserEntity submitBasicKyc(UUID userId, String name, String ip, String deviceId) {
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED", "姓名不能为空");
        }
        UserEntity u = userRepo.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        u.setName(name.trim());
        u.setKycStatus(KycStatus.VERIFIED);
        u = userRepo.save(u);
        logService.log(u.getId(), "KYC_VERIFIED", ip, deviceId, Map.of());
        return u;
    }
}
