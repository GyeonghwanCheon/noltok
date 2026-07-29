package com.example.noltok.friend;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// Friend는 Hard Delete라 삭제되면 이력이 안 남음(decision-log.md 2026-07-02) —
// friend.deleted Kafka 이벤트를 소비해서 이 테이블에 스냅샷을 남긴다.
// 어뷰징 탐지 등 실제 분석 로직은 이 프로젝트 범위 밖, 파이프라인 자체만 구축
@Entity
@Table(name = "friend_deletion_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FriendDeletionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "friend_id", nullable = false)
    private Long friendId;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Column(name = "deleted_by", nullable = false)
    private Long deletedBy;

    @Column(name = "friend_since", nullable = false)
    private LocalDateTime friendSince;

    @Column(name = "deleted_at", nullable = false)
    private LocalDateTime deletedAt;

    private FriendDeletionHistory(Long friendId, Long requesterId, Long receiverId,
                                   Long deletedBy, LocalDateTime friendSince, LocalDateTime deletedAt) {
        this.friendId = friendId;
        this.requesterId = requesterId;
        this.receiverId = receiverId;
        this.deletedBy = deletedBy;
        this.friendSince = friendSince;
        this.deletedAt = deletedAt;
    }

    public static FriendDeletionHistory create(Long friendId, Long requesterId, Long receiverId,
                                                Long deletedBy, LocalDateTime friendSince, LocalDateTime deletedAt) {
        return new FriendDeletionHistory(friendId, requesterId, receiverId, deletedBy, friendSince, deletedAt);
    }
}
