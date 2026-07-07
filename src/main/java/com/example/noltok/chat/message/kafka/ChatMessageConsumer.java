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
        // 1. DB 저장 (기존 ChatMessageService.sendMessage()에 있던 로직)
        ChatMessage message = ChatMessage.createText(event.roomId(), event.senderId(), event.content());
        chatMessageRepository.save(message);

        // 2. 발신자 닉네임 조회
        User sender = userRepository.findById(event.senderId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 3. 방 구독자 전원에게 브로드캐스트
        ChatMessageResponse response = ChatMessageResponse.of(message, sender.getNickname());
        simpMessagingTemplate.convertAndSend("/topic/rooms/" + event.roomId(), response);
    }
}
