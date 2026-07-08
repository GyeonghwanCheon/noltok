package com.example.noltok.notification.kafka;

import com.example.noltok.notification.Notification;
import com.example.noltok.notification.NotificationRepository;
import com.example.noltok.notification.dto.response.NotificationResponse;
import com.example.noltok.notification.sse.SseEmitterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationRepository notificationRepository;
    private final SseEmitterRepository sseEmitterRepository;

    @KafkaListener(topics = NotificationKafkaConfig.NOTIFICATION_TOPIC,
            groupId = "notification-group",
            containerFactory = "notificationKafkaListenerContainerFactory")
    @Transactional
    public void consume(NotificationEvent event) {
        // 1. DB 저장 (오프라인 유저도 나중에 목록 조회로 확인 가능)
        Notification notification = Notification.create(event.receiverId(), event.type(), event.content());
        notificationRepository.save(notification);

        // 2. SSE로 실시간 전달 (연결 없으면 SseEmitterRepository가 조용히 무시)
        sseEmitterRepository.send(event.receiverId(), NotificationResponse.of(notification));
    }
}
