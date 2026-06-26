package com.example.noltok.chat.dto.response;

import com.example.noltok.chat.ChatRoom;
import com.example.noltok.chat.ChatRoomRole;

import java.time.LocalDate;

public record ChatRoomResponse(

        Long roomId,
        String roomname,
        String type,
        int memberCount,
        String myRole,
        LocalDate createdAt

) {
    public static ChatRoomResponse of(ChatRoom room, int memberCount, ChatRoomRole myRole) {
        return new ChatRoomResponse(
                room.getId(),
                room.getRoomname(),
                room.getType().name(),
                memberCount,
                myRole.name(),
                room.getCreatedAt().toLocalDate()
        );
    }
}
