package com.example.noltok.chat.message.kafka;

import com.example.noltok.block.BlockCacheService;
import com.example.noltok.chat.ChatRoomMember;
import com.example.noltok.chat.ChatRoomMemberRepository;
import com.example.noltok.chat.UnreadCountCacheService;
import com.example.noltok.chat.message.ChatMessage;
import com.example.noltok.chat.message.ChatMessageRepository;
import com.example.noltok.chat.message.dto.response.ChatMessageResponse;
import com.example.noltok.global.presence.UserPresenceService;
import com.example.noltok.notification.NotificationType;
import com.example.noltok.notification.kafka.NotificationProducer;
import com.example.noltok.user.UserProfileCacheService;
import com.example.noltok.user.dto.UserProfileDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatMessageConsumer {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserProfileCacheService userProfileCacheService;
    private final SimpMessagingTemplate simpMessagingTemplate;
    private final UserPresenceService userPresenceService;
    private final NotificationProducer notificationProducer;
    private final UnreadCountCacheService unreadCountCacheService;
    private final BlockCacheService blockCacheService;

    @KafkaListener(topics = KafkaConfig.CHAT_MESSAGE_TOPIC,
            groupId = "chat-message-group",
            containerFactory = "chatMessageKafkaListenerContainerFactory")
    @Transactional
    public void consume(List<ChatMessageEvent> events) {
        // 1. 이벤트별로 알맞은 팩토리 메서드로 엔티티 생성 후 배치 저장
        List<ChatMessage> messages = events.stream()
                .map(event -> switch (event.type()) {
                    case TEXT -> ChatMessage.createText(event.roomId(), event.senderId(), event.content());
                    case IMAGE -> ChatMessage.createImage(event.roomId(), event.senderId(), event.fileUrl());
                    case FILE -> ChatMessage.createFile(event.roomId(), event.senderId(), event.fileUrl(), event.content());
                })
                .toList();
        List<ChatMessage> savedMessages = chatMessageRepository.saveAll(messages);

        // 2. 배치 내 발신자 중복 제거 후 프로필 캐시로 배치 조회
        List<Long> senderIds = events.stream().map(ChatMessageEvent::senderId).distinct().toList();
        Map<Long, UserProfileDto> profilesById = userProfileCacheService.getProfiles(senderIds);

        // 3. 배치 내 방 중복 제거 후 활성 멤버 배치 조회
        List<Long> roomIds = events.stream().map(ChatMessageEvent::roomId).distinct().toList();
        Map<Long, List<Long>> memberIdsByRoom = chatRoomMemberRepository.findByChatRoomIdInAndIsActiveTrue(roomIds).stream()
                .collect(Collectors.groupingBy(
                        member -> member.getChatRoom().getId(),
                        Collectors.mapping(ChatRoomMember::getUserId, Collectors.toList())));

        // 4. 메시지별 브로드캐스트 + 오프라인 멤버 알림 발행 (개별 실패는 격리)
        Set<Long> unreadInvalidateTargets = new HashSet<>();
        for (int i = 0; i < events.size(); i++) {
            ChatMessageEvent event = events.get(i);
            ChatMessage message = savedMessages.get(i);
            try {
                UserProfileDto sender = profilesById.get(event.senderId());
                if (sender == null) {
                    throw new IllegalStateException("발신자를 찾을 수 없습니다: senderId=" + event.senderId());
                }

                ChatMessageResponse response = ChatMessageResponse.of(message, sender.nickname());

                // 나를 차단한 사람은 실시간 메시지도 받지 않음
                Set<Long> blockedMeIds = new HashSet<>(blockCacheService.getBlockedMe(event.senderId()));
                List<Long> otherMemberIds = memberIdsByRoom.getOrDefault(event.roomId(), List.of()).stream()
                        .filter(userId -> !userId.equals(event.senderId()))
                        .filter(userId -> !blockedMeIds.contains(userId))
                        .toList();

                // 공유 토픽 대신 개인 큐로 전송 (수신자별 차단 필터링을 위해 필수)
                String destination = "/queue/rooms/" + event.roomId();
                simpMessagingTemplate.convertAndSendToUser(String.valueOf(event.senderId()), destination, response);
                otherMemberIds.forEach(userId -> simpMessagingTemplate.convertAndSendToUser(String.valueOf(userId), destination, response));

                String content = sender.nickname() + ": " + message.toPreviewText();
                otherMemberIds.stream()
                        .filter(userId -> !userPresenceService.isOnline(userId))
                        .forEach(userId -> notificationProducer.publish(userId, NotificationType.CHAT_MESSAGE, content));

                unreadInvalidateTargets.addAll(otherMemberIds);
            } catch (Exception e) {
                log.error("채팅 메시지 후속 처리 실패 (roomId={}, senderId={}) - 저장은 완료됨, 브로드캐스트/알림만 건너뜀",
                        event.roomId(), event.senderId(), e);
            }
        }

        // 5. 배치 전체에서 중복 제거한 유저만 안읽음 캐시 무효화
        unreadInvalidateTargets.forEach(unreadCountCacheService::invalidate);
    }
}
