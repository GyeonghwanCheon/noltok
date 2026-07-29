package com.example.noltok.friend.kafka;

import java.time.LocalDateTime;

public record FriendDeletedEvent(
        Long friendId,
        Long requesterId,
        Long receiverId,
        Long deletedBy,
        LocalDateTime friendSince,
        LocalDateTime deletedAt
) {
}
