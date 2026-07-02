package com.example.noltok.friend.dto;

import com.example.noltok.friend.Friend;
import com.example.noltok.user.User;

import java.time.LocalDate;

public record ReceivedFriendRequestDto(
        Long friendId,
        Long requesterId,
        String requesterNickname,
        String requesterProfileImageUrl,
        LocalDate requestedAt
) {
    // requestedAt은 updatedAt 기준 (친구 요청 전송 API와 동일 원칙)
    public static ReceivedFriendRequestDto of(Friend friend, User requester) {
        return new ReceivedFriendRequestDto(
                friend.getId(),
                requester.getId(),
                requester.getNickname(),
                requester.getProfileImageUrl(),
                friend.getUpdatedAt().toLocalDate()
        );
    }
}
