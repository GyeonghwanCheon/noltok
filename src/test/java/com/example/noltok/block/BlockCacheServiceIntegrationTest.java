package com.example.noltok.block;

import com.example.noltok.support.AbstractIntegrationTest;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// BlockCacheService가 실제 Redis(Set)+MySQL로 캐시-어사이드 패턴을
// 정확히 수행하는지 검증하는 통합 테스트 — 차단 0건(빈 결과)도 캐싱되는지가 핵심
class BlockCacheServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private BlockCacheService blockCacheService;
    @Autowired
    private BlockRepository blockRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;

    private User saveUser(String nickname) {
        return userRepository.save(User.create(nickname + "@test.com", "encoded-pw", nickname));
    }

    @Test
    void 캐시가_비어있으면_DB에서_조회한_뒤_Redis_Set에_실제로_저장한다() {
        // given
        User blocker = saveUser("차단캐시차단자1");
        User blocked = saveUser("차단캐시대상1");
        blockRepository.save(Block.create(blocker.getId(), blocked.getId()));

        // when
        List<Long> result = blockCacheService.getBlockedByMe(blocker.getId());

        // then: 반환값 확인
        assertThat(result).containsExactly(blocked.getId());

        // then: 실제 Redis Set에 캐싱됐는지 직접 확인 (sentinel "0" + 실제 id)
        Set<String> cached = redisTemplate.opsForSet().members("block:blocked_by_me:" + blocker.getId());
        assertThat(cached).contains("0", String.valueOf(blocked.getId()));
    }

    @Test
    void 차단이_0건이어도_다음_조회때_DB를_다시_조회하지_않는다() {
        // given: 차단 이력이 전혀 없는 유저
        User noBlocks = saveUser("차단없는유저1");

        // when: 첫 조회 — DB에서 빈 결과를 캐싱해야 함
        List<Long> first = blockCacheService.getBlockedByMe(noBlocks.getId());
        assertThat(first).isEmpty();

        // then: sentinel만 저장돼서 "캐시 있음(빈 결과)" 상태가 Redis에 실제로 남아있는지 확인
        Set<String> cached = redisTemplate.opsForSet().members("block:blocked_by_me:" + noBlocks.getId());
        assertThat(cached).containsExactly("0");

        // when: DB에 몰래 차단을 하나 추가해도(캐시 우회) 캐시가 있으면 그 값을 안 봄
        User target = saveUser("차단없는유저_대상");
        blockRepository.save(Block.create(noBlocks.getId(), target.getId()));

        // then: 캐시가 여전히 비어있는 그대로 반환됨 (DB를 다시 안 봤다는 뜻)
        assertThat(blockCacheService.getBlockedByMe(noBlocks.getId())).isEmpty();
    }

    @Test
    void invalidate하면_양쪽_방향_캐시가_모두_지워지고_다음_조회때_DB에서_다시_읽어온다() {
        // given: blocker가 blocked를 차단, 양쪽 다 미리 캐싱시켜 놓음
        User blocker = saveUser("차단캐시차단자2");
        User blocked = saveUser("차단캐시대상2");
        blockRepository.save(Block.create(blocker.getId(), blocked.getId()));
        blockCacheService.getBlockedByMe(blocker.getId());
        blockCacheService.getBlockedMe(blocked.getId());

        // when: 무효화
        blockCacheService.invalidate(blocker.getId(), blocked.getId());

        // then: Redis에서 양쪽 키 다 실제로 지워짐
        assertThat(redisTemplate.opsForSet().members("block:blocked_by_me:" + blocker.getId())).isEmpty();
        assertThat(redisTemplate.opsForSet().members("block:blocked_me:" + blocked.getId())).isEmpty();

        // then: 다시 조회하면 DB 값으로 재캐싱됨
        assertThat(blockCacheService.getBlockedByMe(blocker.getId())).containsExactly(blocked.getId());
        assertThat(blockCacheService.getBlockedMe(blocked.getId())).containsExactly(blocker.getId());
    }

    @Test
    void getBlockedMe는_나를_차단한_사람_목록을_반대_방향으로_조회한다() {
        // given: blockerA와 blockerB 둘 다 target을 차단
        User blockerA = saveUser("차단캐시차단자A");
        User blockerB = saveUser("차단캐시차단자B");
        User target = saveUser("차단캐시피차단자");
        blockRepository.save(Block.create(blockerA.getId(), target.getId()));
        blockRepository.save(Block.create(blockerB.getId(), target.getId()));

        // when
        List<Long> result = blockCacheService.getBlockedMe(target.getId());

        // then
        assertThat(result).containsExactlyInAnyOrder(blockerA.getId(), blockerB.getId());
    }
}
