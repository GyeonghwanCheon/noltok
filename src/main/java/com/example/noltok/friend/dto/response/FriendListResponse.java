package com.example.noltok.friend.dto.response;

import com.example.noltok.friend.dto.FriendDto;

import java.util.List;

public record FriendListResponse(
        List<FriendDto> friends
) {
    public static FriendListResponse of(List<FriendDto> friends) {
        return new FriendListResponse(friends);
    }
}
