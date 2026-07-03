package com.example.noltok.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // OPEN/OPEN_PRIVATE 채팅방만 검색 (DIRECT, GROUP 제외)
    // → DIRECT는 이름이 없고 private한 성격
    // → GROUP은 초대제로 바뀌어서 검색해도 못 들어감 (docs/decision-log.md 2026-07-03)
    @Query("""
        SELECT r FROM ChatRoom r
        WHERE r.roomname LIKE %:roomname%
        AND r.type IN ('OPEN', 'OPEN_PRIVATE')
        AND r.isActive = true
    """)
    List<ChatRoom> searchByRoomname(@Param("roomname") String roomname);

    // DIRECT 채팅방 중복 체크
    // → 두 유저 간 DIRECT 방이 이미 있는지 확인
    // → chat_room_members에서 두 userId가 모두 속한 DIRECT 방 조회
    @Query("""
        SELECT r FROM ChatRoom r
        WHERE r.type = 'DIRECT'
        AND r.isActive = true
        AND EXISTS (
            SELECT m1 FROM ChatRoomMember m1
            WHERE m1.chatRoom = r AND m1.userId = :userId1 AND m1.isActive = true
        )
        AND EXISTS (
            SELECT m2 FROM ChatRoomMember m2
            WHERE m2.chatRoom = r AND m2.userId = :userId2 AND m2.isActive = true
        )
    """)
    List<ChatRoom> findDirectRoom(
            @Param("userId1") Long userId1,
            @Param("userId2") Long userId2
    );
}
