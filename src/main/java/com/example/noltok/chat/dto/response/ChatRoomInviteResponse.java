package com.example.noltok.chat.dto.response;

import com.example.noltok.chat.dto.MemberDto;

import java.util.List;

public record ChatRoomInviteResponse(
        Long roomId,
        List<MemberDto> invitedMembers,
        String message
) {
    public static ChatRoomInviteResponse of(Long roomId, List<MemberDto> invitedMembers, List<String> nicknames) {
        return new ChatRoomInviteResponse(
                roomId,
                invitedMembers,
                String.join(", ", nicknames) + "를 초대했습니다."
        );
    }
}
