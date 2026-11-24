package com.studyspace.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录成功返回：令牌 + 用户信息
 */
@Data
@AllArgsConstructor
public class LoginResponse {

    /** 访问令牌，之后每个请求放在请求头 Authorization: Bearer {token} */
    private String token;

    /** 固定 Bearer */
    private String tokenType;

    /** 有效期（秒） */
    private Long expiresIn;

    private UserVO user;
}
