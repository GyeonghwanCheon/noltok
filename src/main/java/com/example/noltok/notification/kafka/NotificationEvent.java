package com.example.noltok.notification.kafka;

import com.example.noltok.notification.NotificationType;

public record NotificationEvent(
        Long receiverId,
        NotificationType type,
        String content
) {
}
