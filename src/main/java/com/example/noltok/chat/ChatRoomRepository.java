package com.example.noltok.chat;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    // OPEN/OPEN_PRIVATE만 검색 — DIRECT는 이름 없음, GROUP은 초대제라 제외
    // 커서 기반 페이지네이션 (Friend/Block과 동일 패턴: id DESC, Slice)
    @Query("""
        SELECT r FROM ChatRoom r
        WHERE r.roomname LIKE %:roomname%
        AND r.type IN ('OPEN', 'OPEN_PRIVATE')
        AND r.isActive = true
        ORDER BY r.id DESC
    """)
    Slice<ChatRoom> searchByRoomnameOrderByIdDesc(@Param("roomname") String roomname, Pageable pageable);

    @Query("""
        SELECT r FROM ChatRoom r
        WHERE r.roomname LIKE %:roomname%
        AND r.type IN ('OPEN', 'OPEN_PRIVATE')
        AND r.isActive = true
        AND r.id < :cursor
        ORDER BY r.id DESC
    """)
    Slice<ChatRoom> searchByRoomnameAndIdLessThanOrderByIdDesc(
            @Param("roomname") String roomname, @Param("cursor") Long cursor, Pageable pageable);

    // 두 유저 간 기존 DIRECT 방 중복 체크
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
