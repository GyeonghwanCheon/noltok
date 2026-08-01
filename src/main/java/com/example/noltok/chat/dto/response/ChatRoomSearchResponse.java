package com.example.noltok.chat.dto.response;

import com.example.noltok.chat.dto.SearchRoomDto;

import java.util.List;

public record ChatRoomSearchResponse(
        List<SearchRoomDto> rooms,
        boolean hasNext,
        Long nextCursor
) {
    // nextCursor는 목록의 마지막(가장 오래된) 방의 roomId (id DESC 정렬 기준)
    public static ChatRoomSearchResponse of(List<SearchRoomDto> rooms, boolean hasNext) {
        Long nextCursor = rooms.isEmpty() ? null : rooms.get(rooms.size() - 1).roomId();
        return new ChatRoomSearchResponse(rooms, hasNext, nextCursor);
    }
}
