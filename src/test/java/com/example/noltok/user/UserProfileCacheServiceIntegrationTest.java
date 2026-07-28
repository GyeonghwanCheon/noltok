package com.example.noltok.user;

import com.example.noltok.support.AbstractIntegrationTest;
import com.example.noltok.user.dto.UserProfileDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

// UserProfileCacheService가 실제 Redis(MGET)+MySQL로 캐시-어사이드 패턴을
// 정확히 수행하는지 검증하는 통합 테스트
class UserProfileCacheServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserProfileCacheService userProfileCacheService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private User saveUser(String nickname) {
        return userRepository.save(User.create(nickname + "@test.com", "encoded-pw", nickname));
    }

    @Test
    void 캐시가_비어있으면_DB에서_조회한_뒤_Redis에_실제로_저장한다() {
        // given
        User user = saveUser("캐시테스트1");

        // when
        Map<Long, UserProfileDto> result = userProfileCacheService.getProfiles(List.of(user.getId()));

        // then: 반환값 확인
        assertThat(result.get(user.getId()).nickname()).isEqualTo("캐시테스트1");

        // then: 실제 Redis에 캐싱됐는지 직접 확인
        String cached = redisTemplate.opsForValue().get("user_profile:" + user.getId());
        assertThat(cached).isNotNull().contains("캐시테스트1");
    }

    @Test
    void 캐시_히트면_DB를_다시_조회하지_않고_캐시값을_그대로_반환한다() {
        // given: 먼저 캐싱시켜 놓음
        User user = saveUser("캐시테스트2");
        userProfileCacheService.getProfiles(List.of(user.getId()));

        // when: DB의 실제 값을 캐시 우회해서 바꿔치기 (캐시가 우선되는지 확인용)
        redisTemplate.opsForValue().set("user_profile:" + user.getId(),
                "{\"userId\":" + user.getId() + ",\"nickname\":\"캐시된값\",\"profileImageUrl\":null}");

        Map<Long, UserProfileDto> result = userProfileCacheService.getProfiles(List.of(user.getId()));

        // then: DB의 "캐시테스트2"가 아니라 캐시에 있던 "캐시된값"이 반환됨
        assertThat(result.get(user.getId()).nickname()).isEqualTo("캐시된값");
    }

    @Test
    void invalidate하면_다음_조회때_DB에서_다시_읽어온다() {
        // given
        User user = saveUser("캐시테스트3");
        userProfileCacheService.getProfiles(List.of(user.getId()));

        // when: 무효화
        userProfileCacheService.invalidate(user.getId());

        // then: Redis에서 실제로 지워짐
        assertThat(redisTemplate.opsForValue().get("user_profile:" + user.getId())).isNull();

        // then: 다시 조회하면 DB 값으로 재캐싱됨
        Map<Long, UserProfileDto> result = userProfileCacheService.getProfiles(List.of(user.getId()));
        assertThat(result.get(user.getId()).nickname()).isEqualTo("캐시테스트3");
    }

    @Test
    void 여러_유저를_한번에_조회하면_캐시_히트와_미스가_섞여도_전부_반환한다() {
        // given: user1은 미리 캐싱, user2는 캐싱 안 함
        User user1 = saveUser("배치캐시1");
        User user2 = saveUser("배치캐시2");
        userProfileCacheService.getProfiles(List.of(user1.getId()));

        // when
        Map<Long, UserProfileDto> result =
                userProfileCacheService.getProfiles(List.of(user1.getId(), user2.getId()));

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(user1.getId()).nickname()).isEqualTo("배치캐시1");
        assertThat(result.get(user2.getId()).nickname()).isEqualTo("배치캐시2");
    }
}
