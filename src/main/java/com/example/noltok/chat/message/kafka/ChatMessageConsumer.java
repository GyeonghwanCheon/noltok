package com.example.noltok.chat.message.kafka;

import com.example.noltok.chat.message.ChatMessage;
import com.example.noltok.chat.message.ChatMessageRepository;
import com.example.noltok.chat.message.dto.response.ChatMessageResponse;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatMessageConsumer {

    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @KafkaListener(topics = KafkaConfig.CHAT_MESSAGE_TOPIC,
            groupId = "chat-message-group",
            containerFactory = "chatMessageKafkaListenerContainerFactory")
    @Transactional
    public void consume(ChatMessageEvent event) {
        // 1. 타입별로 알맞은 팩토리 메서드로 DB 저장
        ChatMessage message = switch (event.type()) {
            case TEXT -> ChatMessage.createText(event.roomId(), event.senderId(), event.content());
            case IMAGE -> ChatMessage.createImage(event.roomId(), event.senderId(), event.fileUrl());
            case FILE -> ChatMessage.createFile(event.roomId(), event.senderId(), event.fileUrl(), event.content());
        };
        chatMessageRepository.save(message);

        // 2. 발신자 닉네임 조회
        User sender = userRepository.findById(event.senderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 3. 방 구독자 전원에게 브로드캐스트
        ChatMessageResponse response = ChatMessageResponse.of(message, sender.getNickname());
        simpMessagingTemplate.convertAndSend("/topic/rooms/" + event.roomId(), response);
    }
}
