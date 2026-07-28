package com.example.noltok.friend.dto;

import com.example.noltok.friend.Friend;
import com.example.noltok.user.dto.UserProfileDto;

import java.time.LocalDate;

public record SentFriendRequestDto(
        Long friendId,
        Long receiverId,
        String receiverNickname,
        String receiverProfileImageUrl,
        LocalDate requestedAt
) {
    // requestedAt은 updatedAt 기준 (친구 요청 전송 API와 동일 원칙)
    public static SentFriendRequestDto of(Friend friend, UserProfileDto receiver) {
        return new SentFriendRequestDto(
                friend.getId(),
                receiver.userId(),
                receiver.nickname(),
                receiver.profileImageUrl(),
                friend.getUpdatedAt().toLocalDate()
        );
    }
}
