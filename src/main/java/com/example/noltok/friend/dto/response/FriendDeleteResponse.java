package com.example.noltok.friend.dto.response;

public record FriendDeleteResponse(
        Long friendId,
        String message
) {
    public static FriendDeleteResponse of(Long friendId, String friendNickname) {
        return new FriendDeleteResponse(
                friendId,
                friendNickname + "님을 친구에서 삭제했습니다."
        );
    }
}
