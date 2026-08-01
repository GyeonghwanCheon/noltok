package com.example.noltok.chat.message.kafka;

import com.example.noltok.block.Block;
import com.example.noltok.block.BlockRepository;
import com.example.noltok.chat.ChatRoom;
import com.example.noltok.chat.ChatRoomMember;
import com.example.noltok.chat.ChatRoomMemberRepository;
import com.example.noltok.chat.ChatRoomRepository;
import com.example.noltok.chat.ChatRoomRole;
import com.example.noltok.chat.ChatRoomType;
import com.example.noltok.chat.UnreadCountCacheService;
import com.example.noltok.chat.message.ChatMessageRepository;
import com.example.noltok.chat.message.ChatMessageType;
import com.example.noltok.notification.NotificationRepository;
import com.example.noltok.notification.NotificationType;
import com.example.noltok.support.AbstractIntegrationTest;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

// Kafka(발행→소비) + MySQL(저장) + Redis(캐시/알림) 전체 파이프라인을
// 실제 인프라로 검증하는 통합 테스트 — 이 흐름은 Mockito로는 원천적으로
// 검증 불가능함 (Consumer가 실제로 메시지를 받아 처리하는지 자체가 대상)
class ChatMessageConsumerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ChatMessageProducer chatMessageProducer;
    @Autowired
    private ChatMessageRepository chatMessageRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UnreadCountCacheService unreadCountCacheService;
    @Autowired
    private ChatRoomRepository chatRoomRepository;
    @Autowired
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BlockRepository blockRepository;

    @Test
    void 메시지_발행시_실제로_소비되어_저장되고_오프라인_수신자에게_알림이_생성되고_캐시가_무효화된다() {
        // given: 방 하나에 발신자(온라인 가정) + 수신자(오프라인, presence 등록 안 함)
        User sender = userRepository.save(User.create("sender@test.com", "pw", "발신자"));
        User receiver = userRepository.save(User.create("receiver@test.com", "pw", "수신자"));
        ChatRoom room = chatRoomRepository.save(ChatRoom.create("통합테스트방", ChatRoomType.OPEN, sender.getId(), null));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, sender.getId(), ChatRoomRole.ADMIN));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, receiver.getId(), ChatRoomRole.MEMBER));

        // 수신자의 안읽은 캐시를 미리 채워둠 → 메시지 도착 후 무효화되는지 확인하기 위함
        unreadCountCacheService.put(receiver.getId(), Map.of());
        assertThat(unreadCountCacheService.get(receiver.getId())).isPresent();

        // when: 실제 Kafka 토픽으로 메시지 발행
        chatMessageProducer.publish(room.getId(), sender.getId(), ChatMessageType.TEXT, "통합테스트 메시지", null);

        // then: Consumer가 비동기로 처리할 때까지 폴링 대기 (최대 10초)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            // 1. 메시지가 실제 MySQL에 저장됐는지
            List<com.example.noltok.chat.message.ChatMessage> messages =
                    chatMessageRepository.findByRoomIdAndSenderIdNotInOrderByIdDesc(room.getId(), List.of(-1L),
                            org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).getContent()).isEqualTo("통합테스트 메시지");

            // 2. 오프라인인 수신자에게 CHAT_MESSAGE 알림이 실제로 생성됐는지
            List<com.example.noltok.notification.Notification> notifications =
                    notificationRepository.findByReceiverIdOrderByIdDesc(receiver.getId(),
                            org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
            assertThat(notifications).hasSize(1);
            assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.CHAT_MESSAGE);
            assertThat(notifications.get(0).getContent()).contains("발신자").contains("통합테스트 메시지");

            // 3. 수신자의 안읽은 캐시가 실제로 무효화(삭제)됐는지
            assertThat(unreadCountCacheService.get(receiver.getId())).isEmpty();
        });
    }

    @Test
    void 발신자를_차단한_멤버는_알림도_안읽음_무효화도_받지_않는다() {
        // given: 방 하나에 발신자 + 발신자를 차단한 멤버(차단자) + 차단 안 한 일반 멤버
        User sender = userRepository.save(User.create("blocksender@test.com", "pw", "차단발신자"));
        User blocker = userRepository.save(User.create("blocker@test.com", "pw", "차단자"));
        User normalMember = userRepository.save(User.create("normal@test.com", "pw", "일반멤버"));
        ChatRoom room = chatRoomRepository.save(ChatRoom.create("차단필터링테스트방", ChatRoomType.OPEN, sender.getId(), null));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, sender.getId(), ChatRoomRole.ADMIN));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, blocker.getId(), ChatRoomRole.MEMBER));
        chatRoomMemberRepository.save(ChatRoomMember.create(room, normalMember.getId(), ChatRoomRole.MEMBER));
        blockRepository.save(Block.create(blocker.getId(), sender.getId()));

        unreadCountCacheService.put(blocker.getId(), Map.of());
        unreadCountCacheService.put(normalMember.getId(), Map.of());

        // when
        chatMessageProducer.publish(room.getId(), sender.getId(), ChatMessageType.TEXT, "차단 필터링 메시지", null);

        // then: 메시지가 실제로 저장될 때까지 기다린 뒤(=Consumer 처리 완료 신호로 삼음)
        //       차단자는 아무것도 못 받고, 일반 멤버는 평소처럼 받는지 함께 확인
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<com.example.noltok.chat.message.ChatMessage> messages =
                    chatMessageRepository.findByRoomIdAndSenderIdNotInOrderByIdDesc(room.getId(), List.of(-1L),
                            org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
            assertThat(messages).hasSize(1);

            assertThat(notificationRepository.findByReceiverIdOrderByIdDesc(blocker.getId(),
                    org.springframework.data.domain.PageRequest.of(0, 10)).getContent()).isEmpty();
            assertThat(notificationRepository.findByReceiverIdOrderByIdDesc(normalMember.getId(),
                    org.springframework.data.domain.PageRequest.of(0, 10)).getContent()).hasSize(1);

            assertThat(unreadCountCacheService.get(blocker.getId())).isPresent();
            assertThat(unreadCountCacheService.get(normalMember.getId())).isEmpty();
        });
    }
}
