package com.example.noltok.friend.dto.response;

import com.example.noltok.friend.dto.SentFriendRequestDto;

import java.util.List;

public record FriendSentListResponse(
        List<SentFriendRequestDto> requests,
        boolean hasNext,
        Long nextCursor
) {
    public static FriendSentListResponse of(List<SentFriendRequestDto> requests, boolean hasNext) {
        Long nextCursor = requests.isEmpty() ? null : requests.get(requests.size() - 1).friendId();
        return new FriendSentListResponse(requests, hasNext, nextCursor);
    }
}
