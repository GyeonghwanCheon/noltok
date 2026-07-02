package com.example.noltok.friend.dto.response;

import com.example.noltok.friend.dto.SentFriendRequestDto;

import java.util.List;

public record FriendSentListResponse(
        List<SentFriendRequestDto> requests
) {
    public static FriendSentListResponse of(List<SentFriendRequestDto> requests) {
        return new FriendSentListResponse(requests);
    }
}
