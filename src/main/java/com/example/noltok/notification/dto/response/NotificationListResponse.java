package com.example.noltok.notification.dto.response;

import java.util.List;

public record NotificationListResponse(
        List<NotificationResponse> notifications,
        boolean hasNext,
        Long nextCursor
) {
    // 알림은 최신순 그대로 유지하므로, nextCursor는 목록의 마지막(가장 오래된) 알림 id
    public static NotificationListResponse of(List<NotificationResponse> notifications, boolean hasNext) {
        Long nextCursor = notifications.isEmpty() ? null : notifications.get(notifications.size() - 1).notificationId();
        return new NotificationListResponse(notifications, hasNext, nextCursor);
    }
}
