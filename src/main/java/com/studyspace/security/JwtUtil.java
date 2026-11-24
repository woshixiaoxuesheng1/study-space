package com.studyspace.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：生成令牌、解析令牌
 * <p>令牌里只放 id、学号、角色，绝不能放密码或敏感信息——
 * JWT 的负载是 Base64 编码，不是加密，谁都能解开看。</p>
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMinutes;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expire-minutes:120}") long expireMinutes) {
        // HS256 要求密钥至少 256 位（32 字节），太短会在启动时直接抛异常
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMinutes = expireMinutes;
    }

    /** 令牌有效期，单位秒 */
    public long getExpiresInSeconds() {
        return expireMinutes * 60;
    }

    /** 生成令牌：sub 存用户 id */
    public String generateToken(Long userId, String studentNo, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expireMinutes * 60 * 1000);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("studentNo", studentNo)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析并校验令牌
     *
     * @throws io.jsonwebtoken.JwtException 签名不对或已过期
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
