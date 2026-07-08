package com.example.noltok.notification.dto.response;

import com.example.noltok.notification.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        String type,
        String content,
        boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse of(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType().name(),
                notification.getContent(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
