package com.example.noltok.friend.dto.response;

import com.example.noltok.friend.dto.FriendDto;

import java.util.List;

public record FriendListResponse(
        List<FriendDto> friends,
        boolean hasNext,
        Long nextCursor
) {
    // nextCursor는 목록의 마지막(가장 오래된) 친구 관계의 friendId (id DESC 정렬 기준)
    public static FriendListResponse of(List<FriendDto> friends, boolean hasNext) {
        Long nextCursor = friends.isEmpty() ? null : friends.get(friends.size() - 1).friendId();
        return new FriendListResponse(friends, hasNext, nextCursor);
    }
}
