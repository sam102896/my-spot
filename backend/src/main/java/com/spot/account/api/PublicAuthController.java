package com.spot.account.api;

import com.spot.account.entity.UserEntity;
import com.spot.account.service.AuthService;
import com.spot.common.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/auth")
public class PublicAuthController {
    private final AuthService authService;

    public PublicAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public RegisterRes register(@RequestBody RegisterReq req, HttpServletRequest http) {
        UserEntity u = authService.register(req.email, req.phone, req.password, RequestContext.ip(http),
                RequestContext.deviceId(http));
        return new RegisterRes(u.getId().toString());
    }

    @PostMapping("/login/otp")
    public OtpRes requestLoginOtp(@RequestBody LoginOtpReq req, HttpServletRequest http) {
        String otp = authService.requestOtpForLogin(req.identifier, req.password, RequestContext.ip(http),
                RequestContext.deviceId(http));
        return new OtpRes(otp);
    }

    @PostMapping("/login")
    public TokenRes login(@RequestBody LoginReq req, HttpServletRequest http) {
        String token = authService.login(req.identifier, req.otp, RequestContext.ip(http),
                RequestContext.deviceId(http));
        return new TokenRes(token);
    }

    @PostMapping("/password/reset/otp")
    public OtpRes requestResetOtp(@RequestBody ResetOtpReq req, HttpServletRequest http) {
        String otp = authService.requestOtpForResetPassword(req.identifier, RequestContext.ip(http),
                RequestContext.deviceId(http));
        return new OtpRes(otp);
    }

    @PostMapping("/password/reset")
    public OkRes resetPassword(@RequestBody ResetReq req, HttpServletRequest http) {
        authService.resetPasswordConfirm(req.identifier, req.otp, req.newPassword, RequestContext.ip(http),
                RequestContext.deviceId(http));
        return new OkRes(true);
    }

    public record RegisterReq(String email, String phone, @NotBlank String password) {
    }

    public record RegisterRes(String userId) {
    }

    public record LoginOtpReq(@NotBlank String identifier, @NotBlank String password) {
    }

    public record LoginReq(@NotBlank String identifier, @NotBlank String otp) {
    }

    public record ResetOtpReq(@NotBlank String identifier) {
    }

    public record ResetReq(@NotBlank String identifier, @NotBlank String otp, @NotBlank String newPassword) {
    }

    public record OtpRes(String otp) {
    }

    public record TokenRes(String token) {
    }

    public record OkRes(boolean ok) {
    }
}
