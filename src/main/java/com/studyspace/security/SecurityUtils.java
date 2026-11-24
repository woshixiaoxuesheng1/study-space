package com.studyspace.security;

import com.studyspace.common.BusinessException;
import com.studyspace.common.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 获取当前登录用户的工具
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** 当前登录用户，未登录时抛 401 */
    public static LoginUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof LoginUser loginUser)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return loginUser;
    }

    /** 当前登录用户 id */
    public static Long currentUserId() {
        return currentUser().getId();
    }
}
