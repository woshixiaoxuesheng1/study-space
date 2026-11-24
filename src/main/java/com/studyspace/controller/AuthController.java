package com.studyspace.controller;

import com.studyspace.common.Result;
import com.studyspace.dto.LoginRequest;
import com.studyspace.dto.RegisterRequest;
import com.studyspace.security.JwtAuthenticationFilter;
import com.studyspace.service.UserService;
import com.studyspace.vo.LoginVO;
import com.studyspace.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册、登录、退出（A-01 / A-02）
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    /** A-01 注册，学号做账号 */
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success("注册成功", userService.register(request));
    }

    /** A-02 登录，返回令牌 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.success("登录成功", userService.login(request));
    }

    /**
     * 退出登录（F-01 的"退出"）
     * <p>把当前令牌从 Redis 白名单删掉，之后这个令牌再请求就是 401，
     * 不用等 JWT 自然过期。</p>
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = JwtAuthenticationFilter.HEADER, required = false) String authorization) {
        if (authorization != null && authorization.startsWith(JwtAuthenticationFilter.PREFIX)) {
            userService.logout(authorization.substring(JwtAuthenticationFilter.PREFIX.length()).trim());
        }
        return Result.success("已退出登录", null);
    }
}
