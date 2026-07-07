package com.example.noltok.chat.dto.response;

public record ChatRoomReadResponse(
        Long roomId,
        Long lastReadMessageId
) {
    public static ChatRoomReadResponse of(Long roomId, Long lastReadMessageId) {
        return new ChatRoomReadResponse(roomId, lastReadMessageId);
    }
}
