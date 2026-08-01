package com.example.noltok.chat.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 최신 메시지부터 조회 (첫 페이지) — excludedSenderIds는 조회자가 차단한 유저 목록
    // (BlockCacheService.getBlockedByMe()), 차단한 사람이 없으면 항상 존재하지 않는
    // sentinel(-1) 하나를 넣어서 호출 — NOT IN 빈 컬렉션을 피하기 위함
    Slice<ChatMessage> findByRoomIdAndSenderIdNotInOrderByIdDesc(Long roomId, List<Long> excludedSenderIds, Pageable pageable);

    // cursor(마지막으로 받은 메시지 id)보다 오래된 메시지 조회 (이전 메시지 불러오기)
    Slice<ChatMessage> findByRoomIdAndSenderIdNotInAndIdLessThanOrderByIdDesc(
            Long roomId, List<Long> excludedSenderIds, Long cursor, Pageable pageable);

    // 읽음 처리용: 방의 최신 메시지 1건 조회
    Optional<ChatMessage> findTopByRoomIdOrderByIdDesc(Long roomId);

    // 여러 방의 마지막 메시지 배치 조회 (N+1 방지, greatest-n-per-group 패턴)
    @Query("""
        SELECT cm FROM ChatMessage cm
        WHERE cm.id IN (
            SELECT MAX(cm2.id) FROM ChatMessage cm2
            WHERE cm2.roomId IN :roomIds
            GROUP BY cm2.roomId
        )
    """)
    List<ChatMessage> findLastMessagesByRoomIds(@Param("roomIds") List<Long> roomIds);
}
