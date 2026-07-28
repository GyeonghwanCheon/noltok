package com.example.noltok.chat.dto;

import com.example.noltok.chat.ChatRoomMember;
import com.example.noltok.user.dto.UserProfileDto;

public record MemberDto(
        Long userId,
        String nickname,
        String role,
        String profileImageUrl
) {
    public static MemberDto of(ChatRoomMember member, UserProfileDto user) {
        return new MemberDto(
                user.userId(),
                user.nickname(),
                member.getRole().name(),
                user.profileImageUrl()
        );
    }
}

