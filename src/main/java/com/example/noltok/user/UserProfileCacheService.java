package com.example.noltok.user;

import com.example.noltok.user.dto.UserProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Chat/Friend/Block 등 여러 도메인에서 반복되는 "유저 프로필 배치 조회" 캐시.
// 무효화가 프로필 수정 시점에만 확실히 일어나는 저빈도 쓰기 데이터라
// unreadCount 캐시(10분)보다 훨씬 긴 TTL을 안전망으로 둠
@Slf4j
@Component
@RequiredArgsConstructor
public class UserProfileCacheService {

    private static final String KEY_PREFIX = "user_profile:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // 캐시 히트는 그대로, 미스는 DB 배치 조회 후 재캐싱해서 합쳐 반환
    public Map<Long, UserProfileDto> getProfiles(List<Long> userIds) {
        List<Long> distinctIds = userIds.stream().distinct().toList();
        if (distinctIds.isEmpty()) {
            return Map.of();
        }

        // 1. Redis MGET으로 한 번에 조회
        List<String> keys = distinctIds.stream().map(id -> KEY_PREFIX + id).toList();
        List<String> cachedValues = redisTemplate.opsForValue().multiGet(keys);

        Map<Long, UserProfileDto> result = new HashMap<>();
        List<Long> missedIds = new ArrayList<>();
        for (int i = 0; i < distinctIds.size(); i++) {
            String raw = cachedValues == null ? null : cachedValues.get(i);
            if (raw != null) {
                result.put(distinctIds.get(i), deserialize(raw));
            } else {
                missedIds.add(distinctIds.get(i));
            }
        }

        // 2. 미스만 DB에서 배치 조회 후 캐싱
        if (!missedIds.isEmpty()) {
            for (User user : userRepository.findAllById(missedIds)) {
                UserProfileDto profile = UserProfileDto.from(user);
                result.put(user.getId(), profile);
                put(user.getId(), profile);
            }
        }

        return result;
    }

    public void put(Long userId, UserProfileDto profile) {
        redisTemplate.opsForValue().set(KEY_PREFIX + userId, serialize(profile), TTL);
    }

    // 프로필 수정(닉네임/이미지 변경) 시점에 호출 — 다음 조회 때 DB에서 재캐싱됨
    public void invalidate(Long userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }

    private String serialize(UserProfileDto profile) {
        return objectMapper.writeValueAsString(profile);
    }

    private UserProfileDto deserialize(String raw) {
        return objectMapper.readValue(raw, UserProfileDto.class);
    }
}
