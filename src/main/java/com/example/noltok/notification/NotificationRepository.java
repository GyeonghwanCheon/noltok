package com.example.noltok.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // 최신 알림부터 조회 (첫 페이지)
    Slice<Notification> findByReceiverIdOrderByIdDesc(Long receiverId, Pageable pageable);

    // cursor(마지막으로 받은 알림 id)보다 오래된 알림 조회
    Slice<Notification> findByReceiverIdAndIdLessThanOrderByIdDesc(Long receiverId, Long cursor, Pageable pageable);
}
