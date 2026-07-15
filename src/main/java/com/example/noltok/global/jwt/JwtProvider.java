package com.example.noltok.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class JwtProvider {

    private final SecretKey secretKey;
    private final long accessExpiration;
    private final long refreshExpiration;
    private final TokenBlacklistService tokenBlacklistService;

    // @Value를 필드가 아닌 생성자로 받음 — 테스트 시 직접 값을 넣어 생성 가능
    public JwtProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-expiration}") long accessExpiration,
            @Value("${jwt.refresh-expiration}") long refreshExpiration,
            TokenBlacklistService tokenBlacklistService) {

        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    // Access Token 생성
    public String generateAccessToken(Long userId) {
        return generateToken(userId, accessExpiration);
    }

    // Refresh Token 생성
    public String generateRefreshToken(Long userId) {
        return generateToken(userId, refreshExpiration);
    }

    // 공통 토큰 생성 로직
    private String generateToken(Long userId, long expiration) {
        Date now = new Date();
        Date expiredAt = new Date(now.getTime() + expiration);

        // jti(고유 식별자) 추가 — iat/exp가 초 단위라 같은 초에 재발급하면
        // jti 없이는 이전 토큰과 완전히 동일한 문자열이 나올 수 있음
        return Jwts.builder()
                .id(UUID.randomUUID().toString())  // jti — 토큰 고유 식별자
                .subject(String.valueOf(userId))  // 토큰 주체 = userId
                .issuedAt(now)                    // 발급 시간
                .expiration(expiredAt)            // 만료 시간
                .signWith(secretKey)              // 서명 (HS256)
                .compact();
    }

    // 토큰 유효성 검증
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);

            // 블랙리스트 체크 — REST/WebSocket 둘 다 이 메서드 하나만 거치므로 여기 한 곳으로 충분
            if (tokenBlacklistService.isBlacklisted(token)) {
                return false;
            }

            return true;
        } catch (ExpiredJwtException e) {
            log.warn("만료된 JWT 토큰입니다.");
        } catch (MalformedJwtException e) {
            log.warn("잘못된 JWT 토큰입니다.");
        } catch (UnsupportedJwtException e) {
            log.warn("지원하지 않는 JWT 토큰입니다.");
        } catch (IllegalArgumentException e) {
            log.warn("JWT 토큰이 비어있습니다.");
        }
        return false;
    }

    // 토큰에서 userId 추출
    public Long getUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return Long.parseLong(claims.getSubject());
    }

    // 토큰의 만료 시각 추출 (로그아웃 시 블랙리스트 TTL 계산용)
    public Date getExpiration(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getExpiration();
    }
}
