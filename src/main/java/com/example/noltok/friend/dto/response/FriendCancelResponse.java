package com.example.noltok.friend.dto.response;

public record FriendCancelResponse(
        Long friendId,
        String message
) {
    public static FriendCancelResponse of(Long friendId, String receiverNickname) {
        return new FriendCancelResponse(
                friendId,
                receiverNickname + "님에게 보낸 친구 요청을 취소했습니다."
        );
    }
}
