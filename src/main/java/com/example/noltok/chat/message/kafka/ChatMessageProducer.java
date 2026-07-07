package com.example.noltok.chat.message.kafka;

import com.example.noltok.chat.message.ChatMessageType;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatMessageProducer {

    private final KafkaTemplate<String, ChatMessageEvent> chatMessageKafkaTemplate;

    // key로 roomId를 써서 같은 방의 메시지는 항상 같은 파티션으로 가도록(순서 보장) 함
    public void publish(Long roomId, Long senderId, ChatMessageType type, String content, String fileUrl) {
        ChatMessageEvent event = new ChatMessageEvent(roomId, senderId, type, content, fileUrl);
        chatMessageKafkaTemplate.send(KafkaConfig.CHAT_MESSAGE_TOPIC, roomId.toString(), event);
    }
}
