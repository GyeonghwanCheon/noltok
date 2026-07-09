package com.example.noltok.chat.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 최신 메시지부터 조회 (첫 페이지)
    Slice<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    // cursor(마지막으로 받은 메시지 id)보다 오래된 메시지 조회 (이전 메시지 불러오기)
    Slice<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long cursor, Pageable pageable);

    // 읽음 처리용: 방의 최신 메시지 1건 조회
    Optional<ChatMessage> findTopByRoomIdOrderByIdDesc(Long roomId);

    // 여러 방의 마지막 메시지를 배치로 조회 (N+1 방지)
    // → greatest-n-per-group 패턴: 방마다 가장 큰 id(=최신 메시지)만 골라 조회
    //   (docs/optimization-log.md [4] 해결, 2026-07-09)
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
