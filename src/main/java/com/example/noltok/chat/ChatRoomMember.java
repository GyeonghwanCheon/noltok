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
        ),
        indexes = @Index(name = "idx_chat_room_members_user_id_is_active", columnList = "user_id, is_active")
)
// uniqueConstraints: 같은 유저의 같은 방 중복 등록 방지
// indexes: (room_id,user_id) 인덱스는 room_id가 왼쪽이라 user_id 단독 조회엔 못 써서 별도 추가
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom chatRoom;
    // 멤버 조회 시 채팅방 정보가 항상 필요해서 @ManyToOne 매핑

    @Column(name = "user_id", nullable = false)
    private Long userId;
    // User는 userId만 저장 — 필요할 때만 별도 조회

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private ChatRoomRole role;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;
    // 아직 아무 메시지도 안 읽었으면 null

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

    // 재입장 처리 — 새로 만들지 않고 재활성화, joinedAt 갱신, role은 MEMBER로 고정
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
