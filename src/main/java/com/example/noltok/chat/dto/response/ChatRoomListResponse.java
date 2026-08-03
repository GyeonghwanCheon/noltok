package com.example.noltok.chat.dto.response;

import com.example.noltok.chat.dto.ChatRoomSummaryDto;

import java.time.LocalDateTime;
import java.util.List;

public record ChatRoomListResponse(
        List<ChatRoomSummaryDto> rooms,
        boolean hasNext,
        LocalDateTime nextCursorTimestamp,
        Long nextCursorRoomId
) {
    public static ChatRoomListResponse of(List<ChatRoomSummaryDto> rooms, boolean hasNext,
                                           LocalDateTime nextCursorTimestamp, Long nextCursorRoomId) {
        return new ChatRoomListResponse(rooms, hasNext, nextCursorTimestamp, nextCursorRoomId);
    }
}
