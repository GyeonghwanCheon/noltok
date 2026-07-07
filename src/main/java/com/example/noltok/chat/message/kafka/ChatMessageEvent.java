package com.example.noltok.chat.message.kafka;

import com.example.noltok.chat.message.ChatMessageType;

public record ChatMessageEvent(
        Long roomId,
        Long senderId,
        ChatMessageType type,
        String content,
        String fileUrl
) {
}
