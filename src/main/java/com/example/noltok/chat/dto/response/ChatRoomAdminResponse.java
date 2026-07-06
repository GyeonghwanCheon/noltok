package com.example.noltok.chat.dto.response;

public record ChatRoomAdminResponse(
        Long roomId,
        Long previousAdminUserId,
        Long newAdminUserId,
        String message
) {
    public static ChatRoomAdminResponse of(Long roomId, Long previousAdminUserId, Long newAdminUserId,
                                            String previousAdminNickname, String newAdminNickname) {
        return new ChatRoomAdminResponse(
                roomId,
                previousAdminUserId,
                newAdminUserId,
                "관리자가 " + previousAdminNickname + "님에서 " + newAdminNickname + "님으로 변경되었습니다."
        );
    }
}
