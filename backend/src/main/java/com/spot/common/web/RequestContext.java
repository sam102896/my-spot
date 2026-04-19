package com.spot.common.web;

import jakarta.servlet.http.HttpServletRequest;

public final class RequestContext {
    public static final String HEADER_DEVICE_ID = "X-Device-Id";

    public record ClientMeta(String ip, String deviceId) {
    }

    private RequestContext() {
    }

    public static String ip(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    public static String deviceId(HttpServletRequest req) {
        String d = req.getHeader(HEADER_DEVICE_ID);
        if (d == null || d.isBlank()) {
            return "unknown";
        }
        return d.trim();
    }

    public static ClientMeta capture(HttpServletRequest req) {
        return new ClientMeta(ip(req), deviceId(req));
    }
}
