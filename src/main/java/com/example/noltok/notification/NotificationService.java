package com.example.noltok.notification;

import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.notification.dto.response.NotificationListResponse;
import com.example.noltok.notification.dto.response.NotificationReadResponse;
import com.example.noltok.notification.dto.response.NotificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public NotificationListResponse getNotifications(Long userId, Long cursor, int size) {
        // 1. 커서 기반 조회 (id DESC), Slice가 다음 페이지 존재 여부를 자동 판단
        Pageable pageable = PageRequest.of(0, size);
        Slice<Notification> slice = cursor == null
                ? notificationRepository.findByReceiverIdOrderByIdDesc(userId, pageable)
                : notificationRepository.findByReceiverIdAndIdLessThanOrderByIdDesc(userId, cursor, pageable);

        // 2. 최신순 그대로 응답 구성 (알림은 피드 성격이라 뒤집지 않음)
        return NotificationListResponse.of(
                slice.getContent().stream().map(NotificationResponse::of).toList(),
                slice.hasNext()
        );
    }

    @Transactional
    public NotificationReadResponse markAsRead(Long userId, Long notificationId) {
        // 1. 알림 존재 확인
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        // 2. 본인 알림인지 확인
        if (!notification.getReceiverId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_NOTIFICATION_OWNER);
        }

        // 3. 읽음 처리 (이미 읽은 알림이어도 멱등하게 통과)
        notification.markAsRead();

        return NotificationReadResponse.of(notification.getId(), notification.isRead());
    }
}
