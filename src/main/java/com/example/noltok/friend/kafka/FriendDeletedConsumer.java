package com.example.noltok.friend.kafka;

import com.example.noltok.friend.FriendDeletionHistory;
import com.example.noltok.friend.FriendDeletionHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FriendDeletedConsumer {

    private final FriendDeletionHistoryRepository friendDeletionHistoryRepository;

    @KafkaListener(topics = FriendKafkaConfig.FRIEND_DELETED_TOPIC,
            groupId = "friend-deleted-group",
            containerFactory = "friendDeletedKafkaListenerContainerFactory")
    @Transactional
    public void consume(FriendDeletedEvent event) {
        // 이력 스냅샷 저장 — 실제 어뷰징 탐지 분석은 별도 파이프라인의 몫, 여기까지가 이 프로젝트 범위
        FriendDeletionHistory history = FriendDeletionHistory.create(
                event.friendId(),
                event.requesterId(),
                event.receiverId(),
                event.deletedBy(),
                event.friendSince(),
                event.deletedAt()
        );
        friendDeletionHistoryRepository.save(history);
    }
}
