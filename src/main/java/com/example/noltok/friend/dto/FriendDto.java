package com.example.noltok.friend.dto;

import com.example.noltok.friend.Friend;
import com.example.noltok.user.User;

import java.time.LocalDate;

public record FriendDto(
        Long friendId,
        Long userId,
        String nickname,
        String profileImageUrl,
        LocalDate becameFriendAt
) {
    // becameFriendAt은 updatedAt 기준
    // → status가 ACCEPTED로 바뀐 시점(accept() 호출 시점)의 updatedAt
    public static FriendDto of(Friend friend, User friendUser) {
        return new FriendDto(
                friend.getId(),
                friendUser.getId(),
                friendUser.getNickname(),
                friendUser.getProfileImageUrl(),
                friend.getUpdatedAt().toLocalDate()
        );
    }
}
