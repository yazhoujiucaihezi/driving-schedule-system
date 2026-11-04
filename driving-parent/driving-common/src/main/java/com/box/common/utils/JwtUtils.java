package com.box.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类 —— 令牌生成与解析
 * <p>
 * 网关验签、下游取身份都依赖本类，属于全链路鉴权的起点。
 * </p>
 */
@Component
public class JwtUtils {

    private final SecretKey key;
    private final long expiration;
    public enum TokenStatus { VALID, EXPIRED, INVALID }

    public JwtUtils(@Value("${jwt.secret}") String secret,
                    @Value("${jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String generateToken(Long userId, String username, String role, Long relatedId) {
        long now = System.currentTimeMillis();
        Map<String, Object> map = new HashMap<>();
        map.put("userId",userId);
        map.put("username",username);
        map.put("role",role);
        map.put("relatedId",relatedId);
        return Jwts.builder()
                .claims(map)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expiration))
                .signWith(key,Jwts.SIG.HS256)
                .compact();

    }

    public Claims parseToken(String token) {
        if (token == null || token.isBlank()){
            return null;
        }
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public TokenStatus validate(String token) {
        if (token == null || token.isBlank()) return TokenStatus.INVALID;
        try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
            return TokenStatus.VALID;
        } catch (ExpiredJwtException e) {
            return TokenStatus.EXPIRED;
        } catch (JwtException | IllegalArgumentException e) {
            return TokenStatus.INVALID;
        }
    }
}
