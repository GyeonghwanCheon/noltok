package com.example.noltok.chat.message.dto.response;

public record ChatMessageDeleteResponse(
        Long messageId,
        String message
) {
    public static ChatMessageDeleteResponse of(Long messageId) {
        return new ChatMessageDeleteResponse(messageId, "메시지가 삭제되었습니다.");
    }
}
