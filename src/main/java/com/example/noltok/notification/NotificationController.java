package com.example.noltok.notification;

import com.example.noltok.global.response.ApiResponse;
import com.example.noltok.notification.dto.response.NotificationListResponse;
import com.example.noltok.notification.dto.response.NotificationReadResponse;
import com.example.noltok.notification.sse.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterRepository sseEmitterRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationListResponse>> getNotifications(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = Long.parseLong(userDetails.getUsername());
        NotificationListResponse response = notificationService.getNotifications(userId, cursor, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationReadResponse>> markAsRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long notificationId) {
        Long userId = Long.parseLong(userDetails.getUsername());
        NotificationReadResponse response = notificationService.markAsRead(userId, notificationId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // "/subscribe"가 고정 경로라 "/{notificationId}/read"와 겹치지 않음 (세그먼트 개수가 다름)
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        return sseEmitterRepository.register(userId);
    }
}
