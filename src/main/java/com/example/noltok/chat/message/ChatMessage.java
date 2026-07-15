package com.example.noltok.chat.message;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages",
        indexes = @Index(name = "idx_chat_messages_room_id_id", columnList = "room_id, id"))
// room_id + id 역순(커서 페이지네이션) 조회가 사실상 전부라 우선순위 가장 높음
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;
    // @ManyToOne 대신 roomId만 저장 — ChatRoom 엔티티 불필요, JOIN 방지

    @Column(name = "sender_id", nullable = false)
    private Long senderId;
    // User도 동일한 이유로 id만 저장, 필요한 경우에만 별도 조회

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;
    // TEXT 타입 메시지일 때만 값 존재

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ChatMessageType type;

    @Column(name = "file_url")
    private String fileUrl;
    // IMAGE/FILE 타입일 때만 값 존재

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    // 메시지는 수정 기능이 없는 불변 도메인이라 BaseEntity 대신 createdAt만 직접 관리

    private ChatMessage(Long roomId, Long senderId, ChatMessageType type, String content, String fileUrl) {
        this.roomId = roomId;
        this.senderId = senderId;
        this.type = type;
        this.content = content;
        this.fileUrl = fileUrl;
        this.createdAt = LocalDateTime.now();
    }

    public static ChatMessage createText(Long roomId, Long senderId, String content) {
        return new ChatMessage(roomId, senderId, ChatMessageType.TEXT, content, null);
    }

    public static ChatMessage createImage(Long roomId, Long senderId, String fileUrl) {
        return new ChatMessage(roomId, senderId, ChatMessageType.IMAGE, null, fileUrl);
    }

    // fileName: 원본 파일명, content 필드를 그대로 재사용 (별도 필드 추가하지 않음)
    public static ChatMessage createFile(Long roomId, Long senderId, String fileUrl, String fileName) {
        return new ChatMessage(roomId, senderId, ChatMessageType.FILE, fileName, fileUrl);
    }

    // 채팅방 목록/알림에서 공통으로 쓰는 타입별 미리보기 문구
    public String toPreviewText() {
        return switch (type) {
            case TEXT -> content;
            case IMAGE -> "[사진]";
            case FILE -> "[파일] " + content;
        };
    }
}
