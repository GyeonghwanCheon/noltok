package com.example.noltok.notification;

import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.notification.dto.response.NotificationListResponse;
import com.example.noltok.notification.dto.response.NotificationReadResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// DB 없이 순수 JVM에서 도는 단위 테스트 — NotificationRepository는 Mock
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    private final Long userId = 1L;
    private final Long notificationId = 100L;

    private Notification testNotification(Long id, Long receiverId, boolean read) {
        Notification notification = Notification.create(receiverId, NotificationType.FRIEND_REQUEST, "알림 내용");
        if (read) notification.markAsRead();
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    // ── getNotifications() ─────────────────────────────────────

    @Test
    void getNotifications_cursor가_없으면_최신순으로_첫_페이지를_조회한다() {
        // given
        Notification n1 = testNotification(2L, userId, false);
        Notification n2 = testNotification(1L, userId, true);
        given(notificationRepository.findByReceiverIdOrderByIdDesc(anyLong(), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(n1, n2), PageRequest.of(0, 20), false));

        // when
        NotificationListResponse response = notificationService.getNotifications(userId, null, 20);

        // then
        assertThat(response.notifications()).hasSize(2);
        assertThat(response.hasNext()).isFalse();
        verify(notificationRepository).findByReceiverIdOrderByIdDesc(anyLong(), any(Pageable.class));
        verify(notificationRepository, never()).findByReceiverIdAndIdLessThanOrderByIdDesc(anyLong(), anyLong(), any());
    }

    @Test
    void getNotifications_cursor가_있으면_그보다_오래된_알림을_조회한다() {
        // given
        Notification older = testNotification(5L, userId, false);
        given(notificationRepository.findByReceiverIdAndIdLessThanOrderByIdDesc(eq(userId), eq(10L), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(older), PageRequest.of(0, 20), true));

        // when
        NotificationListResponse response = notificationService.getNotifications(userId, 10L, 20);

        // then: 알림은 최신순 그대로 유지 → nextCursor는 마지막(가장 오래된) 알림 id
        assertThat(response.hasNext()).isTrue();
        assertThat(response.nextCursor()).isEqualTo(5L);
        verify(notificationRepository, never()).findByReceiverIdOrderByIdDesc(anyLong(), any());
    }

    // ── markAsRead() ───────────────────────────────────────────

    @Test
    void markAsRead_정상_읽음처리() {
        // given
        Notification notification = testNotification(notificationId, userId, false);
        given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));

        // when
        NotificationReadResponse response = notificationService.markAsRead(userId, notificationId);

        // then
        assertThat(response.isRead()).isTrue();
        assertThat(notification.isRead()).isTrue();
    }

    @Test
    void markAsRead_이미_읽은_알림이어도_멱등하게_통과한다() {
        // given: 이미 읽음 처리된 알림
        Notification alreadyRead = testNotification(notificationId, userId, true);
        given(notificationRepository.findById(notificationId)).willReturn(Optional.of(alreadyRead));

        // when & then: 예외 없이 그대로 통과
        NotificationReadResponse response = notificationService.markAsRead(userId, notificationId);
        assertThat(response.isRead()).isTrue();
    }

    @Test
    void markAsRead_존재하지_않으면_예외() {
        // given
        given(notificationRepository.findById(notificationId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationService.markAsRead(userId, notificationId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    void markAsRead_본인_알림이_아니면_예외() {
        // given: userId(1)가 아니라 다른 사람(999)의 알림
        Notification othersNotification = testNotification(notificationId, 999L, false);
        given(notificationRepository.findById(notificationId)).willReturn(Optional.of(othersNotification));

        // when & then
        assertThatThrownBy(() -> notificationService.markAsRead(userId, notificationId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_NOTIFICATION_OWNER);
    }
}
