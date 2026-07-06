package com.example.noltok.chat.dto.response;

public record ChatRoomLeaveResponse(
        Long roomId,
        String message
) {
    public static ChatRoomLeaveResponse ofDirect(Long roomId, String otherNickname) {
        return new ChatRoomLeaveResponse(roomId, otherNickname + "님과의 채팅방에서 나왔습니다.");
    }

    public static ChatRoomLeaveResponse of(Long roomId, String roomname) {
        return new ChatRoomLeaveResponse(roomId, roomname + "에서 나왔습니다.");
    }
}
