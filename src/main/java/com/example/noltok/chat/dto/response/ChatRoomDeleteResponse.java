package com.example.noltok.chat.dto.response;

public record ChatRoomDeleteResponse(
        Long roomId,
        String message
) {
    public static ChatRoomDeleteResponse of(Long roomId, String roomname) {
        return new ChatRoomDeleteResponse(roomId, roomname + "이 삭제되었습니다.");
    }
}
