package com.example.noltok.block;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

// 채팅 메시지 파이프라인 전용 차단 관계 캐시 (고빈도 조회라 캐싱 적용)
@Component
@RequiredArgsConstructor
public class BlockCacheService {

    private static final String BLOCKED_BY_ME_PREFIX = "block:blocked_by_me:";
    private static final String BLOCKED_ME_PREFIX = "block:blocked_me:";
    // 실제 userId(1부터 시작)와 겹치지 않는 sentinel — Redis Set이 비어있으면
    // "캐시 미스"와 구분이 안 되므로, 결과가 0건이어도 이 값만은 항상 같이 저장해둠
    private static final String EMPTY_MARKER = "0";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;
    private final BlockRepository blockRepository;

    // 내가 차단한 사람 목록 (메시지 이력 조회 필터링용)
    public List<Long> getBlockedByMe(Long userId) {
        return getOrLoad(BLOCKED_BY_ME_PREFIX + userId,
                () -> blockRepository.findBlockedIdsByBlockerIdAndIsActiveTrue(userId));
    }

    // 나를 차단한 사람 목록 (실시간 브로드캐스트·알림 대상 필터링용)
    public List<Long> getBlockedMe(Long userId) {
        return getOrLoad(BLOCKED_ME_PREFIX + userId,
                () -> blockRepository.findBlockerIdsByBlockedIdAndIsActiveTrue(userId));
    }

    private List<Long> getOrLoad(String key, Supplier<List<Long>> loader) {
        Set<String> cached = redisTemplate.opsForSet().members(key);
        if (cached != null && !cached.isEmpty()) {
            return cached.stream()
                    .filter(v -> !EMPTY_MARKER.equals(v))
                    .map(Long::parseLong)
                    .toList();
        }

        List<Long> loaded = loader.get();
        String[] toStore = Stream.concat(Stream.of(EMPTY_MARKER), loaded.stream().map(String::valueOf))
                .toArray(String[]::new);
        redisTemplate.opsForSet().add(key, toStore);
        redisTemplate.expire(key, TTL);

        return loaded;
    }

    // 차단/차단 해제 시점에 호출 — 두 유저의 캐시를 각자 관련된 방향으로만 무효화
    // (blockerId의 "내가 차단한" 목록, blockedId의 "나를 차단한" 목록)
    public void invalidate(Long blockerId, Long blockedId) {
        redisTemplate.delete(BLOCKED_BY_ME_PREFIX + blockerId);
        redisTemplate.delete(BLOCKED_ME_PREFIX + blockedId);
    }
}
