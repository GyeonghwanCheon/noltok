package com.example.noltok.chat.message.dto.response;

import com.example.noltok.chat.message.ChatMessage;

import java.time.LocalDateTime;

public record ChatMessageResponse(
        Long messageId,
        Long roomId,
        Long senderId,
        String senderNickname,
        String content,
        String type,
        String fileUrl,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse of(ChatMessage message, String senderNickname) {
        return new ChatMessageResponse(
                message.getId(),
                message.getRoomId(),
                message.getSenderId(),
                senderNickname,
                message.getContent(),
                message.getType().name(),
                message.getFileUrl(),
                message.getCreatedAt()
        );
    }
}
