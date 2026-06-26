package com.example.noltok.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {


    // 특정 방의 활성 멤버 전체 조회
    List<ChatRoomMember> findByChatRoomIdAndIsActiveTrue(Long roomId);

    // 특정 유저가 특정 방의 활성 멤버인지 확인
    Optional<ChatRoomMember> findByChatRoomIdAndUserIdAndIsActiveTrue(Long roomId, Long userId);

    // 특정 유저의 활성 채팅방 목록 조회
    @Query("""
        SELECT m FROM ChatRoomMember m
        WHERE m.userId = :userId
        AND m.isActive = true
        AND m.chatRoom.isActive = true
    """)
    List<ChatRoomMember> findActiveRoomsByUserId(@Param("userId") Long userId);

    // 특정 방의 활성 멤버 수 조회
    int countByChatRoomIdAndIsActiveTrue(Long roomId);
}
