package com.example.noltok.chat.dto;

import com.example.noltok.chat.ChatRoom;
import com.example.noltok.chat.ChatRoomMember;
import com.example.noltok.chat.message.ChatMessage;

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
    public static ChatRoomSummaryDto of(ChatRoom room, ChatRoomMember member, int unreadCount, ChatMessage lastMessage) {
        return new ChatRoomSummaryDto(
                room.getId(),
                room.getRoomname(),
                room.getType().name(),
                member.getRole().name(),
                lastMessage != null ? lastMessage.toPreviewText() : null,
                unreadCount,
                room.getUpdatedAt().toLocalDate()
        );
    }
}
