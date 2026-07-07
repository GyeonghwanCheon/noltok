package com.example.noltok.chat;


import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_room_members",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"room_id", "user_id"}
        )
)
// uniqueConstraints 이유:
// → 같은 유저가 같은 방에 중복 등록되는 것을 DB 레벨에서 방지
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom chatRoom;
    // ChatRoom은 @ManyToOne으로 연결
    // 이유: 멤버 조회 시 채팅방 정보가 항상 필요하므로 연관관계 매핑

    @Column(name = "user_id", nullable = false)
    private Long userId;
    // User는 userId만 저장
    // 이유: 멤버 조회 시 User 전체 정보가 항상 필요하지 않음
    //       필요한 경우에만 UserRepository로 별도 조회

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private ChatRoomRole role;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;
    // nullable 이유:
    // → 채팅 메시지 기능 구현 전까지 null 허용
    // → 메시지 기능 구현 후 읽음 처리 시 업데이트

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    private ChatRoomMember(ChatRoom chatRoom, Long userId, ChatRoomRole role) {
        this.chatRoom = chatRoom;
        this.userId = userId;
        this.role = role;
        this.isActive = true;
        this.joinedAt = LocalDateTime.now();
    }

    public static ChatRoomMember create(ChatRoom chatRoom, Long userId, ChatRoomRole role) {
        return new ChatRoomMember(chatRoom, userId, role);
    }

    // 관리자 역할 부여 (ADMIN으로 승격)
    public void promoteToAdmin() {
        this.role = ChatRoomRole.ADMIN;
    }

    // 일반 멤버로 강등
    public void demoteToMember() {
        this.role = ChatRoomRole.MEMBER;
    }

    // 채팅방 나가기 (Soft Delete)
    public void deactivate() {
        this.isActive = false;
    }

    // 재입장 처리 (나갔다가 다시 들어오는 케이스)
    // → 새 멤버십 생성 대신 기존 멤버십 재활성화
    // → joinedAt 갱신 이유: 재입장 시점으로 업데이트
    // → role은 MEMBER로 고정 (재입장 시 ADMIN 권한 미부여)
    public void reactivate() {
        this.isActive = true;
        this.role = ChatRoomRole.MEMBER;
        this.joinedAt = LocalDateTime.now();
    }

    // 읽음 처리: 방의 최신 메시지 id까지 읽은 것으로 갱신
    public void markAsRead(Long messageId) {
        this.lastReadMessageId = messageId;
    }

}
