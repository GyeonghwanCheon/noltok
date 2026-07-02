package com.example.noltok.friend.dto.response;

import com.example.noltok.friend.Friend;

public record FriendAcceptResponse(
        Long friendId,
        String friendNickname,
        String status,
        String message
) {
    public static FriendAcceptResponse of(Friend friend, String requesterNickname) {
        return new FriendAcceptResponse(
                friend.getId(),
                requesterNickname,
                friend.getStatus().name(),
                requesterNickname + "님과 친구가 되었습니다."
        );
    }
}
