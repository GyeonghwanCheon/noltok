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

    // 여러 방의 활성 멤버를 한 번에 배치 조회 (Kafka Consumer 배치 처리용, N+1 방지)
    List<ChatRoomMember> findByChatRoomIdInAndIsActiveTrue(List<Long> roomIds);

    // 특정 유저가 특정 방의 활성 멤버인지 확인
    Optional<ChatRoomMember> findByChatRoomIdAndUserIdAndIsActiveTrue(Long roomId, Long userId);

    // JOIN FETCH로 chatRoom 함께 로딩 — 방마다 추가 쿼리 나가는 것 방지
    @Query("""
        SELECT m FROM ChatRoomMember m
        JOIN FETCH m.chatRoom r
        WHERE m.userId = :userId
        AND m.isActive = true
        AND r.isActive = true
    """)
    List<ChatRoomMember> findActiveRoomsByUserId(@Param("userId") Long userId);

    // 특정 방의 활성 멤버 수 조회
    int countByChatRoomIdAndIsActiveTrue(Long roomId);

    // 검색된 채팅방 여러 개의 활성 멤버 수를 배치 조회 (N+1 방지)
    @Query("""
        SELECT m.chatRoom.id AS roomId, COUNT(m) AS memberCount
        FROM ChatRoomMember m
        WHERE m.chatRoom.id IN :roomIds
        AND m.isActive = true
        GROUP BY m.chatRoom.id
    """)
    List<RoomMemberCountProjection> countActiveMembersByChatRoomIds(@Param("roomIds") List<Long> roomIds);

    // isActive 관계없이 조회 — 재입장 시 기존 멤버십 찾아 reactivate()
    Optional<ChatRoomMember> findByChatRoomIdAndUserId(Long roomId, Long userId);

    // 채팅방 삭제 시 전체 활성 멤버 일괄 강제 퇴장 (벌크 쿼리)
    @Modifying
    @Query("UPDATE ChatRoomMember m SET m.isActive = false WHERE m.chatRoom.id = :roomId AND m.isActive = true")
    void deactivateAllByChatRoomId(@Param("roomId") Long roomId);

    // 내 활성 채팅방 전체의 안읽은 메시지 수 배치 조회 (N+1 방지)
    @Query("""
        SELECT m.chatRoom.id AS roomId, COUNT(cm.id) AS unreadCount
        FROM ChatRoomMember m, ChatMessage cm
        WHERE m.userId = :userId
        AND m.isActive = true
        AND m.chatRoom.isActive = true
        AND cm.roomId = m.chatRoom.id
        AND cm.id > COALESCE(m.lastReadMessageId, 0)
        GROUP BY m.chatRoom.id
    """)
    List<UnreadCountProjection> countUnreadMessagesByUserId(@Param("userId") Long userId);
}
