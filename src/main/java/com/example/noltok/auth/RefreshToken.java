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

    // userId를 키로 사용 (유저당 토큰 1개라 자동증가 id 불필요)
    @Id
    private Long userId;

    // findByToken() 조회를 위한 보조 색인
    @Indexed
    private String token;

    // 초 단위 TTL — Redis가 이 시간이 지나면 키를 자동 삭제함
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

    // rotate()는 없음 — AuthService에서 deleteById() 후 create()로 재생성
}
