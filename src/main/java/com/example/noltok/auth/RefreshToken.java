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

    // rotate()(필드 값만 바꿔서 save())는 제거함 — AuthService에서
    // deleteById() 후 create()로 재생성하는 방식이 의도가 더 명확해서 대체함
    // (실제 재발급 버그의 원인은 이 필드 변경 방식이 아니라 JwtProvider가
    // 같은 초 안에서 동일한 토큰을 발급하던 문제였음 — docs/
    // troubleshooting-log.md 2026-07-13 참고)
}
