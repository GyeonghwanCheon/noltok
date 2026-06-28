package com.example.noltok.chat.dto.response;

import com.example.noltok.chat.ChatRoom;
import com.example.noltok.chat.ChatRoomMember;

import java.time.LocalDate;

public record ChatRoomSummaryDto(
        Long roomId,
        String roomname,
        String type,
        String myRole,
        String lastMessage,
        int unreadCount,
        LocalDate updatedAt
) {
    public static ChatRoomSummaryDto of(ChatRoom room, ChatRoomMember member) {
        return new ChatRoomSummaryDto(
                room.getId(),
                room.getRoomname(),
                room.getType().name(),
                member.getRole().name(),
                null,
                0,
                room.getUpdatedAt().toLocalDate()
        );
    }
}
