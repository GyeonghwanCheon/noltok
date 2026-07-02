package com.example.noltok.friend.dto.response;

import com.example.noltok.friend.dto.ReceivedFriendRequestDto;

import java.util.List;

public record FriendReceivedListResponse(
        List<ReceivedFriendRequestDto> requests
) {
    public static FriendReceivedListResponse of(List<ReceivedFriendRequestDto> requests) {
        return new FriendReceivedListResponse(requests);
    }
}
