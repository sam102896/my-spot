package com.spot.security;

import com.spot.common.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class Auth {
    private Auth() {
    }

    public static String requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "未登录");
        }
        return auth.getName();
    }
}
