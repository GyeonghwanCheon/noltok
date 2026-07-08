package com.example.noltok.notification.kafka;

import com.example.noltok.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationProducer {

    private final KafkaTemplate<String, NotificationEvent> notificationKafkaTemplate;

    // key로 receiverId를 써서 같은 유저의 알림은 항상 같은 파티션으로 가도록(순서 보장) 함
    public void publish(Long receiverId, NotificationType type, String content) {
        NotificationEvent event = new NotificationEvent(receiverId, type, content);
        notificationKafkaTemplate.send(NotificationKafkaConfig.NOTIFICATION_TOPIC, receiverId.toString(), event);
    }
}
