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
    // nullable 허용 이유:
    // → DIRECT 채팅방은 이름이 없음
    // → GROUP만 roomname 필수

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ChatRoomType type;
    // EnumType.STRING 이유:
    // → EnumType.ORDINAL은 Enum 순서가 바뀌면 DB 데이터 깨짐
    // → STRING으로 저장하면 "DIRECT", "GROUP" 문자열로 저장되어 안전
    // length=20: OPEN_PRIVATE(12자)까지 저장 가능하도록 확장

    @Column(name = "password", length = 100)
    private String password;
    // OPEN_PRIVATE 타입일 때만 값 존재, 그 외 타입은 null
    // BCrypt 암호화된 값 저장 (평문 저장 금지, docs/decision-log.md 2026-07-03)

    @Column(name = "created_by", nullable = false)
    private Long createdBy;
    // @ManyToOne 대신 userId만 저장하는 이유:
    // → 연관관계 매핑 시 채팅방 조회마다 User JOIN 발생
    // → 생성자 정보가 필요한 경우에만 별도 조회하는 방식이 성능상 유리

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

    // OPEN_PRIVATE 입장 시 비밀번호 검증
    // → 검증 책임을 Entity가 가짐 (Setter 금지와 같은 맥락)
    // → encoder는 Spring Bean이라 Entity가 직접 주입받지 않고 파라미터로 전달받음
    public boolean matchesPassword(String rawPassword, PasswordEncoder passwordEncoder) {
        return password != null && passwordEncoder.matches(rawPassword, password);
    }
}
