package com.spot.account.service;

import com.spot.account.entity.OtpEntity;
import com.spot.account.entity.UserEntity;
import com.spot.account.model.OtpPurpose;
import com.spot.account.model.UserStatus;
import com.spot.account.repo.DeviceRepo;
import com.spot.account.repo.OtpRepo;
import com.spot.account.repo.UserRepo;
import com.spot.common.api.ApiException;
import com.spot.config.AppProperties;
import com.spot.security.JwtService;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepo userRepo;
    private final OtpRepo otpRepo;
    private final DeviceRepo deviceRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OperationLogService logService;
    private final AppProperties props;
    private final SecureRandom rnd = new SecureRandom();

    public AuthService(UserRepo userRepo, OtpRepo otpRepo, DeviceRepo deviceRepo, PasswordEncoder passwordEncoder,
            JwtService jwtService, OperationLogService logService, AppProperties props) {
        this.userRepo = userRepo;
        this.otpRepo = otpRepo;
        this.deviceRepo = deviceRepo;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.logService = logService;
        this.props = props;
    }

    @Transactional
    public UserEntity register(String email, String phone, String password, String ip, String deviceId) {
        if ((email == null || email.isBlank()) && (phone == null || phone.isBlank())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDENTIFIER_REQUIRED", "邮箱或手机号至少提供一个");
        }
        if (password == null || password.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "密码至少8位");
        }
        if (email != null && !email.isBlank() && userRepo.findByEmail(email.trim()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_EXISTS", "邮箱已注册");
        }
        if (phone != null && !phone.isBlank() && userRepo.findByPhone(phone.trim()).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "PHONE_EXISTS", "手机号已注册");
        }
        UserEntity u = new UserEntity();
        u.setEmail(email == null ? null : email.trim());
        u.setPhone(phone == null ? null : phone.trim());
        u.setPasswordHash(passwordEncoder.encode(password));
        u = userRepo.save(u);
        logService.log(u.getId(), "REGISTER", ip, deviceId, Map.of());
        return u;
    }

    @Transactional
    public String requestOtpForLogin(String identifier, String password, String ip, String deviceId) {
        UserEntity u = findUser(identifier);
        ensureActive(u);
        if (!passwordEncoder.matches(password, u.getPasswordHash())) {
            logService.log(u.getId(), "LOGIN_PASSWORD_FAIL", ip, deviceId, Map.of());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "账号或密码错误");
        }
        String otp = genOtp();
        OtpEntity o = new OtpEntity();
        o.setUserId(u.getId());
        o.setPurpose(OtpPurpose.LOGIN);
        o.setCodeHash(passwordEncoder.encode(otp));
        o.setExpiresAt(Instant.now().plus(Duration.ofMinutes(5)));
        otpRepo.save(o);
        logService.log(u.getId(), "LOGIN_OTP_ISSUED", ip, deviceId, Map.of());
        return props.getSecurity().isAllowDevOtpEcho() ? otp : "SENT";
    }

    @Transactional
    public String login(String identifier, String otp, String ip, String deviceId) {
        UserEntity u = findUser(identifier);
        ensureActive(u);

        boolean hasBound = !deviceRepo.findByUserIdOrderByLastSeenAtDesc(u.getId()).isEmpty();
        if (hasBound && deviceRepo.findByUserIdAndDeviceId(u.getId(), deviceId).isEmpty()) {
            logService.log(u.getId(), "LOGIN_BLOCK_UNTRUSTED_DEVICE", ip, deviceId, Map.of());
            throw new ApiException(HttpStatus.FORBIDDEN, "UNTRUSTED_DEVICE", "该设备未绑定，已拦截登录");
        }

        OtpEntity o = otpRepo.findLatestValid(u.getId(), OtpPurpose.LOGIN, Instant.now())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "OTP_EXPIRED", "验证码已过期或不存在"));
        if (!passwordEncoder.matches(otp, o.getCodeHash())) {
            logService.log(u.getId(), "LOGIN_OTP_FAIL", ip, deviceId, Map.of());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "OTP_INVALID", "验证码错误");
        }
        o.setConsumedAt(Instant.now());
        otpRepo.save(o);

        u.setLastLoginAt(Instant.now());
        u.setLastLoginIp(ip);
        u.setLastLoginDeviceId(deviceId);
        userRepo.save(u);

        logService.log(u.getId(), "LOGIN_OK", ip, deviceId, Map.of());
        return jwtService.issueToken(u.getId().toString());
    }

    @Transactional
    public String requestOtpForResetPassword(String identifier, String ip, String deviceId) {
        UserEntity u = findUser(identifier);
        ensureActive(u);
        String otp = genOtp();
        OtpEntity o = new OtpEntity();
        o.setUserId(u.getId());
        o.setPurpose(OtpPurpose.RESET_PASSWORD);
        o.setCodeHash(passwordEncoder.encode(otp));
        o.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        otpRepo.save(o);
        logService.log(u.getId(), "RESET_OTP_ISSUED", ip, deviceId, Map.of());
        return props.getSecurity().isAllowDevOtpEcho() ? otp : "SENT";
    }

    @Transactional
    public void resetPasswordConfirm(String identifier, String otp, String newPassword, String ip, String deviceId) {
        UserEntity u = findUser(identifier);
        ensureActive(u);
        if (newPassword == null || newPassword.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "密码至少8位");
        }
        OtpEntity o = otpRepo.findLatestValid(u.getId(), OtpPurpose.RESET_PASSWORD, Instant.now())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "OTP_EXPIRED", "验证码已过期或不存在"));
        if (!passwordEncoder.matches(otp, o.getCodeHash())) {
            logService.log(u.getId(), "RESET_OTP_FAIL", ip, deviceId, Map.of());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "OTP_INVALID", "验证码错误");
        }
        o.setConsumedAt(Instant.now());
        otpRepo.save(o);
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(u);
        logService.log(u.getId(), "RESET_PASSWORD_OK", ip, deviceId, Map.of());
    }

    @Transactional
    public void changeLoginPassword(UUID userId, String oldPassword, String newPassword, String ip, String deviceId) {
        UserEntity u = userRepo.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        if (!passwordEncoder.matches(oldPassword, u.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "原密码错误");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_PASSWORD", "密码至少8位");
        }
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(u);
        logService.log(u.getId(), "CHANGE_LOGIN_PASSWORD", ip, deviceId, Map.of());
    }

    @Transactional
    public void setOrChangeFundPassword(UUID userId, String loginPassword, String newFundPassword, String ip,
            String deviceId) {
        UserEntity u = userRepo.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        if (!passwordEncoder.matches(loginPassword, u.getPasswordHash())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "登录密码错误");
        }
        if (newFundPassword == null || newFundPassword.length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WEAK_FUND_PASSWORD", "资金密码至少6位");
        }
        u.setFundPasswordHash(passwordEncoder.encode(newFundPassword));
        userRepo.save(u);
        logService.log(u.getId(), "SET_FUND_PASSWORD", ip, deviceId, Map.of());
    }

    public boolean verifyFundPassword(UUID userId, String fundPassword) {
        UserEntity u = userRepo.findById(userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
        if (u.getFundPasswordHash() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "FUND_PASSWORD_NOT_SET", "未设置资金密码");
        }
        return passwordEncoder.matches(fundPassword, u.getFundPasswordHash());
    }

    private void ensureActive(UserEntity u) {
        if (u.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(HttpStatus.FORBIDDEN, "USER_LOCKED", "账号已锁定");
        }
    }

    private UserEntity findUser(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "IDENTIFIER_REQUIRED", "账号不能为空");
        }
        return userRepo.findByIdentifier(identifier.trim())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "账号不存在"));
    }

    private String genOtp() {
        int v = 100000 + rnd.nextInt(900000);
        return Integer.toString(v);
    }
}
