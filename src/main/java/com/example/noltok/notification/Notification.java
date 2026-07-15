package com.example.noltok.notification;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications",
        indexes = @Index(name = "idx_notifications_receiver_id_id", columnList = "receiver_id, id"))
// receiver_id + id 역순(커서 페이지네이션) 조회가 사실상 전부라 우선순위 높음
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;
    // @ManyToOne 대신 receiverId만 저장 — User 엔티티가 따로 필요 없음

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    // 알림은 읽음 여부만 바뀌는 불변 도메인이라 BaseEntity 대신 createdAt만 직접 관리

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
