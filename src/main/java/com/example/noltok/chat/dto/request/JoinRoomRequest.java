package com.example.noltok.chat.dto.request;

public record JoinRoomRequest(

        // OPEN_PRIVATE 타입일 때만 사용, 그 외 타입에서는 무시됨
        String password

) {}
