package com.example.noltok.chat.dto;

import com.example.noltok.chat.ChatRoom;

public record SearchRoomDto(
        Long roomId,
        String roomname,
        String type,
        int memberCount
) {
    public static SearchRoomDto of(ChatRoom room, int memberCount) {
        return new SearchRoomDto(
                room.getId(),
                room.getRoomname(),
                room.getType().name(),
                memberCount
        );
    }
}
