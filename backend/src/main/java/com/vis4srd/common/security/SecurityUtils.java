package com.vis4srd.common.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static AuthUser requireCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw new com.vis4srd.common.exception.BusinessException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "未提供认证 Token");
        }
        return user;
    }
}
