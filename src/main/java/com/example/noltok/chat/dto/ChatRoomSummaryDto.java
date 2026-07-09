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
                toPreview(lastMessage),
                unreadCount,
                room.getUpdatedAt().toLocalDate()
        );
    }

    // 메시지 타입별 목록 미리보기 문구 (카카오톡 등 채팅 리스트의 일반적인 관례)
    private static String toPreview(ChatMessage lastMessage) {
        if (lastMessage == null) {
            return null;
        }
        return switch (lastMessage.getType()) {
            case TEXT -> lastMessage.getContent();
            case IMAGE -> "[사진]";
            case FILE -> "[파일] " + lastMessage.getContent();
        };
    }
}
