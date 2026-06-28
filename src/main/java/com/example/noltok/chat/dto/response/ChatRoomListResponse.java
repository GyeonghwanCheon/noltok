package com.example.noltok.chat.dto.response;

import java.util.List;

public record ChatRoomListResponse(
        List<ChatRoomSummaryDto> rooms
) {
    public static ChatRoomListResponse of(List<ChatRoomSummaryDto> rooms) {
        return new ChatRoomListResponse(rooms);
    }
}
