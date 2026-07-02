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
    // requestedAt은 updatedAt 기준
    // → REJECTED row를 재사용(reopen)한 경우 createdAt은 최초 생성일 그대로라
    //   "이번에 요청을 보낸 시점"을 정확히 표현하려면 updatedAt을 써야 함
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
