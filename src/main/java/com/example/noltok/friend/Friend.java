package com.example.noltok.friend;

import com.example.noltok.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "friends", indexes = {
        @Index(name = "idx_friends_requester_receiver", columnList = "requester_id, receiver_id"),
        @Index(name = "idx_friends_receiver_requester", columnList = "receiver_id, requester_id")
})
// findRelationBetween()이 양방향 OR 조회라 정방향/역방향 인덱스 둘 다 필요
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friend extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;
    // requester/receiver는 userId만 저장 — User JOIN 불필요

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private FriendStatus status;

    private Friend(Long requesterId, Long receiverId) {
        this.requesterId = requesterId;
        this.receiverId = receiverId;
        this.status = FriendStatus.PENDING;
    }

    public static Friend create(Long requesterId, Long receiverId) {
        return new Friend(requesterId, receiverId);
    }

    // REJECTED 상태였던 관계를 재사용 — 새 row 대신 기존 row 갱신 ("관계당 1행" 원칙)
    public void reopen(Long requesterId, Long receiverId) {
        this.requesterId = requesterId;
        this.receiverId = receiverId;
        this.status = FriendStatus.PENDING;
    }

    public void accept() {
        this.status = FriendStatus.ACCEPTED;
    }

    public void reject() {
        this.status = FriendStatus.REJECTED;
    }
}
