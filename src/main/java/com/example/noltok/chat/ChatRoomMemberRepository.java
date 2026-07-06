package com.example.noltok.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // isActive 관계없이 멤버십 조회
    // → 재입장 케이스 처리용
    // → 나갔던 유저(isActive=false)의 멤버십을 찾아서 reactivate() 호출
    Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long roomId, Long userId);

    // 채팅방 삭제 시 전체 활성 멤버 일괄 강제 퇴장
    // → 멤버 수만큼 개별 UPDATE가 나가는 것을 피하기 위해 벌크 쿼리로 처리
    @Modifying
    @Query("UPDATE ChatRoomMember m SET m.isActive = false WHERE m.chatRoom.id = :roomId AND m.isActive = true")
    void deactivateAllByChatRoomId(@Param("roomId") Long roomId);
}
