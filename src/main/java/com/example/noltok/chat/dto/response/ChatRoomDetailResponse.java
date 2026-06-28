package com.example.noltok.chat.dto.response;

import com.example.noltok.chat.ChatRoom;
import com.example.noltok.chat.ChatRoomMember;
import com.example.noltok.chat.dto.MemberDto;

import java.time.LocalDate;
import java.util.List;

public record ChatRoomDetailResponse(
        Long roomId,
        String roomname,
        String type,
        String myRole,
        List<MemberDto> members,
        LocalDate createdAt
) {
    public static ChatRoomDetailResponse of(
            ChatRoom room,
            ChatRoomMember myMembership,
            List<MemberDto> members) {
        return new ChatRoomDetailResponse(
                room.getId(),
                room.getRoomname(),
                room.getType().name(),
                myMembership.getRole().name(),
                members,
                room.getCreatedAt().toLocalDate()
        );
    }
}
