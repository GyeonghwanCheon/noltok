package com.example.noltok.chat;

import com.example.noltok.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "roomname", length = 100)
    private String roomname;
    // nullable 허용 이유:
    // → DIRECT 채팅방은 이름이 없음
    // → GROUP만 roomname 필수

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private ChatRoomType type;
    // EnumType.STRING 이유:
    // → EnumType.ORDINAL은 Enum 순서가 바뀌면 DB 데이터 깨짐
    // → STRING으로 저장하면 "DIRECT", "GROUP" 문자열로 저장되어 안전

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    // @ManyToOne 대신 userId만 저장하는 이유:
    // → 연관관계 매핑 시 채팅방 조회마다 User JOIN 발생
    // → 생성자 정보가 필요한 경우에만 별도 조회하는 방식이 성능상 유리

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
    // 채팅방 삭제 시 Hard Delete 대신 Soft Delete

    private ChatRoom(String roomname, ChatRoomType type, Long createdBy) {
        this.roomname = roomname;
        this.type = type;
        this.createdBy = createdBy;
        this.isActive = true;
    }

    public static ChatRoom create(String roomname, ChatRoomType type, Long createdBy) {
        return new ChatRoom(roomname, type, createdBy);
    }

    // 채팅방 삭제 (Soft Delete)
    public void deactivate() {
        this.isActive = false;
    }
}
