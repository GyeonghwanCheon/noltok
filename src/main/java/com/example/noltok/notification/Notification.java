package com.example.noltok.notification;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;
    // @ManyToOne 대신 receiverId만 저장
    // → 알림 목록 조회는 이미 receiverId(로그인한 유저)를 알고 있는
    //   상태에서 호출되므로 User 엔티티 자체가 필요하지 않음

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    // 알림은 수정 기능이 없는 불변 도메인(읽음 여부만 바뀜)이라
    // BaseEntity(updatedAt 포함) 대신 createdAt만 직접 관리

    private Notification(Long receiverId, NotificationType type, String content) {
        this.receiverId = receiverId;
        this.type = type;
        this.content = content;
        this.isRead = false;
        this.createdAt = LocalDateTime.now();
    }

    public static Notification create(Long receiverId, NotificationType type, String content) {
        return new Notification(receiverId, type, content);
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
