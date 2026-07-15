package com.example.noltok.chat;

import com.example.noltok.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

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
    // nullable 허용 — DIRECT는 이름 없음, GROUP만 필수

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ChatRoomType type;
    // length=20: OPEN_PRIVATE(12자)까지 저장 가능하도록 확장

    @Column(name = "password", length = 100)
    private String password;
    // OPEN_PRIVATE 타입일 때만 값 존재, BCrypt 암호화 저장

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    // @ManyToOne 대신 userId만 저장 — 생성자 정보 필요할 때만 별도 조회

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
    // 채팅방 삭제 시 Hard Delete 대신 Soft Delete

    private ChatRoom(String roomname, ChatRoomType type, Long createdBy, String encodedPassword) {
        this.roomname = roomname;
        this.type = type;
        this.createdBy = createdBy;
        this.password = encodedPassword;
        this.isActive = true;
    }

    public static ChatRoom create(String roomname, ChatRoomType type, Long createdBy, String encodedPassword) {
        return new ChatRoom(roomname, type, createdBy, encodedPassword);
    }

    // 채팅방 삭제 (Soft Delete)
    public void deactivate() {
        this.isActive = false;
    }

    // OPEN_PRIVATE 입장 시 비밀번호 검증 — encoder는 Bean이라 파라미터로 전달받음
    public boolean matchesPassword(String rawPassword, PasswordEncoder passwordEncoder) {
        return password != null && passwordEncoder.matches(rawPassword, password);
    }
}
