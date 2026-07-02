package com.example.noltok.friend;

import com.example.noltok.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "friends")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Friend extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_id", nullable = false)
    private Long requesterId;
    // requester/receiver는 userId만 저장 (User와 @ManyToOne 매핑 안 함)
    // 이유: ChatRoom.createdBy와 동일한 이유로, 조회 시 User JOIN 불필요

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

    // REJECTED 상태였던 관계를 새 요청으로 재사용할 때 사용
    // → 새 row를 만들지 않고 기존 row를 갱신해 "관계당 1행" 원칙 유지
    //   (docs/decision-log.md 2026-07-02 결정)
    public void reopen(Long requesterId, Long receiverId) {
        this.requesterId = requesterId;
        this.receiverId = receiverId;
        this.status = FriendStatus.PENDING;
    }
}
