package com.example.noltok.friend.dto;

import com.example.noltok.friend.Friend;
import com.example.noltok.user.dto.UserProfileDto;

import java.time.LocalDate;

public record FriendDto(
        Long friendId,
        Long userId,
        String nickname,
        String profileImageUrl,
        LocalDate becameFriendAt
) {
    // becameFriendAt은 updatedAt 기준 (accept() 호출 시점)
    public static FriendDto of(Friend friend, UserProfileDto friendUser) {
        return new FriendDto(
                friend.getId(),
                friendUser.userId(),
                friendUser.nickname(),
                friendUser.profileImageUrl(),
                friend.getUpdatedAt().toLocalDate()
        );
    }
}
