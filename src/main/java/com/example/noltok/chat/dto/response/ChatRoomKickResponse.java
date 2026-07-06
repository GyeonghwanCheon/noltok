package com.example.noltok.chat.dto.response;

public record ChatRoomKickResponse(
        Long roomId,
        Long kickedUserId,
        String message
) {
    public static ChatRoomKickResponse of(Long roomId, Long kickedUserId, String nickname) {
        return new ChatRoomKickResponse(
                roomId,
                kickedUserId,
                nickname + "님을 강제 퇴장시켰습니다."
        );
    }
}
