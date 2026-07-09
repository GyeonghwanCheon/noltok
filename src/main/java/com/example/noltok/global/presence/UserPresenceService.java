package com.example.noltok.global.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserPresenceService {

    private static final String KEY_PREFIX = "online:";

    private final StringRedisTemplate redisTemplate;

    // 세션 연결 시 등록 — 유저 1명이 여러 탭/기기로 접속할 수 있어 Set으로 관리
    public void connect(Long userId, String sessionId) {
        redisTemplate.opsForSet().add(KEY_PREFIX + userId, sessionId);
    }

    // 연결 종료 시 해당 세션만 제거 — 다른 세션이 남아있으면 여전히 온라인
    public void disconnect(Long userId, String sessionId) {
        redisTemplate.opsForSet().remove(KEY_PREFIX + userId, sessionId);
    }

    // 활성 세션이 하나라도 남아있으면 온라인
    public boolean isOnline(Long userId) {
        Long count = redisTemplate.opsForSet().size(KEY_PREFIX + userId);
        return count != null && count > 0;
    }
}
