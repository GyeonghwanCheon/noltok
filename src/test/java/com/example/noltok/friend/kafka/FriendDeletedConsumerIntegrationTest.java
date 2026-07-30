package com.example.noltok.friend.kafka;

import com.example.noltok.friend.Friend;
import com.example.noltok.friend.FriendDeletionHistory;
import com.example.noltok.friend.FriendDeletionHistoryRepository;
import com.example.noltok.friend.FriendRepository;
import com.example.noltok.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

// Kafka(발행→소비) + MySQL(이력 저장) 전체 파이프라인을 실제 인프라로 검증
// — Friend는 Hard Delete라 이력이 안 남는 걸 friend.deleted 이벤트로 보완하는
// 파이프라인 자체가 실제로 동작하는지가 검증 대상 (optimization-log.md [9])
class FriendDeletedConsumerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FriendDeletedProducer friendDeletedProducer;
    @Autowired
    private FriendDeletionHistoryRepository friendDeletionHistoryRepository;
    @Autowired
    private FriendRepository friendRepository;

    @Test
    void 친구삭제_이벤트_발행시_실제로_소비되어_이력테이블에_저장된다() {
        // given: 실제로 저장돼 있던(영속화된) 친구 관계 하나
        Friend friend = friendRepository.save(Friend.create(1L, 2L));
        friend.accept();
        friendRepository.save(friend);
        LocalDateTime friendSince = friend.getUpdatedAt();
        Long friendId = friend.getId();

        // when: 실제 Kafka 토픽으로 삭제 이벤트 발행 (삭제 실행자는 requester인 1L)
        friendDeletedProducer.publish(friend, 1L);

        // then: Consumer가 비동기로 처리할 때까지 폴링 대기 (최대 10초)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<FriendDeletionHistory> histories = friendDeletionHistoryRepository.findAll();
            FriendDeletionHistory saved = histories.stream()
                    .filter(h -> h.getFriendId().equals(friendId))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("이력이 아직 저장되지 않음"));

            assertThat(saved.getRequesterId()).isEqualTo(1L);
            assertThat(saved.getReceiverId()).isEqualTo(2L);
            assertThat(saved.getDeletedBy()).isEqualTo(1L);
            // MySQL은 datetime(6) 컬럼에 더 정밀한 값이 들어오면 버리는 게 아니라
            // "반올림"해서 저장함(MySQL 5.6.4부터 공식 문서화된 동작) — truncatedTo()로
            // 맞춰도 반올림 케이스에선 여전히 어긋날 수 있어, 정확히 같은 순간인지만
            // 확인하면 충분하므로 1초 오차를 허용
            assertThat(saved.getFriendSince()).isCloseTo(friendSince, within(1, ChronoUnit.SECONDS));
        });
    }
}
