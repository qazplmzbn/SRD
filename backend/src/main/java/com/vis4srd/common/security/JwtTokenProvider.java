package com.vis4srd.common.security;

import com.vis4srd.common.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    public Map<String, Object> createAccessToken(Long userId, String username, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(jwtProperties.getExpireMinutes() * 60);
        String jti = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role == null ? "user" : role)
                .claim("jti", jti)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey())
                .compact();

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("expires_at", DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(expiresAt.atOffset(ZoneOffset.UTC)));
        result.put("jti", jti);
        return result;
    }

    public AuthUser parseToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return AuthUser.builder()
                .userId(Long.parseLong(claims.getSubject()))
                .username(claims.get("username", String.class))
                .role(claims.get("role", String.class))
                .jti(claims.get("jti", String.class))
                .exp(claims.getExpiration() != null ? claims.getExpiration().toInstant().getEpochSecond() : null)
                .build();
    }

    public boolean isExpired(Exception ex) {
        return ex instanceof ExpiredJwtException;
    }

    private SecretKey signingKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        // HS256 要求密钥足够长；不足时做填充，避免本地短 secret 启动失败
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, keyBytes.length);
            keyBytes = padded;
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
