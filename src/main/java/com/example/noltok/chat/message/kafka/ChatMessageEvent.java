package com.example.noltok.chat.message.kafka;

public record ChatMessageEvent(
        Long roomId,
        Long senderId,
        String content
) {
}
