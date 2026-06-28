package com.example.noltok.chat.dto;

import com.example.noltok.chat.ChatRoomMember;
import com.example.noltok.user.User;

public record MemberDto(
        Long userId,
        String nickname,
        String role,
        String profileImageUrl
) {
    public static MemberDto of(ChatRoomMember member, User user) {
        return new MemberDto(
                user.getId(),
                user.getNickname(),
                member.getRole().name(),
                user.getProfileImageUrl()
        );
    }
}

