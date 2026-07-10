package com.example.noltok.chat;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class UnreadCountCacheService {

    private static final String KEY_PREFIX = "unread:";
    // 유저의 모든 방이 0건(다 읽음)이라 해시가 원래 비어있는 경우와
    // "캐시가 아예 없음"을 구분하기 위한 마커 필드
    private static final String CACHED_MARKER = "_cached";
    // 무효화 지점(메시지 도착/읽음 처리)을 놓쳤을 때를 대비한 안전망 TTL
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public Optional<Map<Long, Integer>> get(Long userId) {
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(KEY_PREFIX + userId);
        if (!raw.containsKey(CACHED_MARKER)) {
            return Optional.empty();
        }
        Map<Long, Integer> result = raw.entrySet().stream()
                .filter(e -> !CACHED_MARKER.equals(e.getKey()))
                .collect(Collectors.toMap(
                        e -> Long.parseLong((String) e.getKey()),
                        e -> Integer.parseInt((String) e.getValue())));
        return Optional.of(result);
    }

    public void put(Long userId, Map<Long, Integer> unreadCountMap) {
        String key = KEY_PREFIX + userId;
        Map<String, String> toStore = new HashMap<>();
        toStore.put(CACHED_MARKER, "1");
        unreadCountMap.forEach((roomId, count) -> toStore.put(String.valueOf(roomId), String.valueOf(count)));
        redisTemplate.opsForHash().putAll(key, toStore);
        redisTemplate.expire(key, TTL);
    }

    public void invalidate(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
