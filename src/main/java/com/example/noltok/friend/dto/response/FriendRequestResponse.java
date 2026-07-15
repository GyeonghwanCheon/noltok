package com.example.noltok.friend.dto.response;

import com.example.noltok.friend.Friend;

import java.time.LocalDate;

public record FriendRequestResponse(
        Long friendId,
        Long receiverId,
        String receiverNickname,
        String status,
        LocalDate requestedAt
) {
    // requestedAt은 updatedAt 기준 (reopen 시 createdAt은 최초 생성일 그대로라 정확한 시점 표현 불가)
    public static FriendRequestResponse of(Friend friend, String receiverNickname) {
        return new FriendRequestResponse(
                friend.getId(),
                friend.getReceiverId(),
                receiverNickname,
                friend.getStatus().name(),
                friend.getUpdatedAt().toLocalDate()
        );
    }
}
