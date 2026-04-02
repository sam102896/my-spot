package com.spot.common.web;

import com.spot.common.api.ApiException;
import com.spot.config.AppProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DevAdminGuard {
    public static final String HEADER_ADMIN_KEY = "X-Admin-Key";

    private final AppProperties props;

    public DevAdminGuard(AppProperties props) {
        this.props = props;
    }

    public void require(HttpServletRequest req) {
        if (!props.getSecurity().isAllowDevAdminKey()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ADMIN_DISABLED", "管理员接口已关闭");
        }
        String key = req.getHeader(HEADER_ADMIN_KEY);
        if (key == null || !key.equals(props.getSecurity().getDevAdminKey())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "ADMIN_KEY_INVALID", "管理员Key错误");
        }
    }
}
