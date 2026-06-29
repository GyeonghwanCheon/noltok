package com.example.noltok.chat.dto.response;

public record ChatRoomJoinResponse(
        Long roomId,
        String myRole,
        String message
) {
    public static ChatRoomJoinResponse of(Long roomId, String myRole, String nickname) {
        return new ChatRoomJoinResponse(
                roomId,
                myRole,
                nickname + "님이 채팅방에 입장했습니다."
        );
    }
}
