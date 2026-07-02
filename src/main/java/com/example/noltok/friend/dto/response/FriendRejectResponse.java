package com.example.noltok.friend.dto.response;

import com.example.noltok.friend.Friend;

public record FriendRejectResponse(
        Long friendId,
        String status,
        String message
) {
    public static FriendRejectResponse of(Friend friend) {
        return new FriendRejectResponse(
                friend.getId(),
                friend.getStatus().name(),
                "친구 요청을 거절하였습니다."
        );
    }
}
