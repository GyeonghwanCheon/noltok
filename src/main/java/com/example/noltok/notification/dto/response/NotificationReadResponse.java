package com.example.noltok.notification.dto.response;

public record NotificationReadResponse(
        Long notificationId,
        boolean isRead
) {
    public static NotificationReadResponse of(Long notificationId, boolean isRead) {
        return new NotificationReadResponse(notificationId, isRead);
    }
}
