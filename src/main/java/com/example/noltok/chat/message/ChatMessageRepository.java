package com.example.noltok.chat.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 최신 메시지부터 조회 (첫 페이지)
    Slice<ChatMessage> findByRoomIdOrderByIdDesc(Long roomId, Pageable pageable);

    // cursor(마지막으로 받은 메시지 id)보다 오래된 메시지 조회 (이전 메시지 불러오기)
    Slice<ChatMessage> findByRoomIdAndIdLessThanOrderByIdDesc(Long roomId, Long cursor, Pageable pageable);
}
