package com.example.noltok.global.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String PREFIX = "blacklist:";

    private final StringRedisTemplate redisTemplate;

    // 토큰이 원래 갖고 있던 남은 만료시간만큼만 TTL을 설정 → Redis가 자동으로 청소
    public void blacklist(String token, long remainingMillis) {
        if (remainingMillis <= 0) {
            return;  // 이미 만료된 토큰은 굳이 블랙리스트에 넣을 필요 없음
        }
        redisTemplate.opsForValue().set(PREFIX + token, "logout", Duration.ofMillis(remainingMillis));
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }
}
