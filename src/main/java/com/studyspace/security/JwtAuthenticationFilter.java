package com.studyspace.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：每个请求先执行这里
 *
 * <p>校验分两步，缺一不可：</p>
 * <ol>
 *   <li>验签 + 验过期（JWT 自带的能力，不需要查库）</li>
 *   <li>查 Redis 白名单（这一步让令牌"可撤销"：退出登录、被顶下线后立刻失效）</li>
 * </ol>
 *
 * <p>任何一步不通过就什么都不做，后面的 Security 配置发现"没登录"，自然返回 401。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "Authorization";
    public static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final LoginSessionStore loginSessionStore;

    // 前端的每次请求都执行该方法
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String token = header.substring(PREFIX.length()).trim();
            try {
                Claims claims = jwtUtil.parseToken(token);
                if (isTokenActive(token)) {
                    LoginUser loginUser = new LoginUser(
                            Long.valueOf(claims.getSubject()),
                            claims.get("studentNo", String.class),
                            claims.get("role", String.class));

                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            loginUser, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + loginUser.getRole())));
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    // 设置认证信息到 SecurityContext
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    log.debug("令牌不在白名单中（已退出或被顶下线）：studentNo={}", claims.get("studentNo", String.class));
                }
            } catch (JwtException | IllegalArgumentException e) {
                // 令牌过期、被篡改、格式不对都走这里，不抛异常，交给 401 入口处理
                log.debug("令牌校验失败：{}", e.getMessage());
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 查 Redis 白名单
     * <p>Redis 挂掉时按"令牌无效"处理（fail-closed）：
     * 宁可让用户重新登录，也不能因为缓存不可用就把门敞开。</p>
     */
    private boolean isTokenActive(String token) {
        try {
            return loginSessionStore.isTokenActive(token);
        } catch (Exception e) {
            log.error("Redis 不可用，令牌白名单校验失败，本次请求按未登录处理", e);
            return false;
        }
    }
}
