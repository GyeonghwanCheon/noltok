package com.example.noltok.auth;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.redis.core.RedisHash;
import org.springframework.data.redis.core.index.Indexed;
import org.springframework.data.redis.core.TimeToLive;

@RedisHash("refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    // userId를 키로 사용 (유저당 토큰 1개라 자동증가 id가 불필요,
    // 기존 MySQL 버전의 findByUserId() 용도를 그대로 살림)
    @Id
    private Long userId;

    // findByToken() 조회를 위한 보조 색인
    @Indexed
    private String token;

    // 초 단위 TTL — Redis가 이 시간이 지나면 키를 자동 삭제함
    // (MySQL 버전의 expiresAt 수동 비교 로직을 대체)
    @TimeToLive
    private Long ttlSeconds;

    private RefreshToken(Long userId, String token, long ttlSeconds) {
        this.userId = userId;
        this.token = token;
        this.ttlSeconds = ttlSeconds;
    }

    public static RefreshToken create(Long userId, String token, long refreshExpiration) {
        return new RefreshToken(userId, token, refreshExpiration / 1000);  // ms → seconds 변환
    }

    // 토큰 갱신 (Refresh Token Rotation 전략)
    // 이유: Refresh Token을 재사용하지 않고 재발급마다 새 토큰으로 교체
    //       탈취된 토큰이 재사용되면 탐지 가능
    // ⚠️ Redis는 JPA와 달리 변경감지가 없으므로, 이 메서드 호출 후
    //    반드시 RefreshTokenRepository.save()를 명시적으로 호출해야 반영됨
    public void rotate(String newToken, long refreshExpiration) {
        this.token = newToken;
        this.ttlSeconds = refreshExpiration / 1000;
    }
}
