package com.example.noltok.friend.kafka;

import com.example.noltok.friend.Friend;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FriendDeletedProducer {

    private final KafkaTemplate<String, FriendDeletedEvent> friendDeletedKafkaTemplate;

    // key로 deletedBy(삭제를 실행한 유저)를 써서 같은 유저의 삭제 이력이 같은 파티션으로 모임
    public void publish(Friend friend, Long deletedBy) {
        FriendDeletedEvent event = new FriendDeletedEvent(
                friend.getId(),
                friend.getRequesterId(),
                friend.getReceiverId(),
                deletedBy,
                friend.getUpdatedAt(),
                java.time.LocalDateTime.now()
        );
        friendDeletedKafkaTemplate.send(FriendKafkaConfig.FRIEND_DELETED_TOPIC, deletedBy.toString(), event);
    }
}
