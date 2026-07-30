package com.example.noltok.friend.dto.response;

import com.example.noltok.friend.dto.ReceivedFriendRequestDto;

import java.util.List;

public record FriendReceivedListResponse(
        List<ReceivedFriendRequestDto> requests,
        boolean hasNext,
        Long nextCursor
) {
    public static FriendReceivedListResponse of(List<ReceivedFriendRequestDto> requests, boolean hasNext) {
        Long nextCursor = requests.isEmpty() ? null : requests.get(requests.size() - 1).friendId();
        return new FriendReceivedListResponse(requests, hasNext, nextCursor);
    }
}
