package com.example.noltok.chat.dto.response;

import com.example.noltok.chat.dto.SearchRoomDto;

import java.util.List;

public record ChatRoomSearchResponse(
        List<SearchRoomDto> rooms
) {
    public static ChatRoomSearchResponse of(List<SearchRoomDto> rooms) {
        return new ChatRoomSearchResponse(rooms);
    }
}
