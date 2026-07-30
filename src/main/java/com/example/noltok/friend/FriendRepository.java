package com.example.noltok.friend;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    // 단방향 저장이라 OR 조건으로 양방향 조회
    @Query("SELECT f FROM Friend f WHERE " +
            "(f.requesterId = :userA AND f.receiverId = :userB) OR " +
            "(f.requesterId = :userB AND f.receiverId = :userA)")
    Optional<Friend> findRelationBetween(@Param("userA") Long userA, @Param("userB") Long userB);

    // status=ACCEPTED, 내가 requester/receiver 어느 쪽이든 포함된 관계 — 커서 기반 페이지네이션
    @Query("SELECT f FROM Friend f WHERE f.status = 'ACCEPTED' " +
            "AND (f.requesterId = :userId OR f.receiverId = :userId) ORDER BY f.id DESC")
    Slice<Friend> findAllAcceptedByUserIdOrderByIdDesc(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT f FROM Friend f WHERE f.status = 'ACCEPTED' " +
            "AND (f.requesterId = :userId OR f.receiverId = :userId) AND f.id < :cursor ORDER BY f.id DESC")
    Slice<Friend> findAllAcceptedByUserIdAndIdLessThanOrderByIdDesc(
            @Param("userId") Long userId, @Param("cursor") Long cursor, Pageable pageable);

    // 방향 고정(receiverId)이라 파생 쿼리로 충분 — 커서 기반 페이지네이션
    Slice<Friend> findByReceiverIdAndStatusOrderByIdDesc(Long receiverId, FriendStatus status, Pageable pageable);

    Slice<Friend> findByReceiverIdAndStatusAndIdLessThanOrderByIdDesc(
            Long receiverId, FriendStatus status, Long cursor, Pageable pageable);

    // findByReceiverId...와 대칭 (방향만 requesterId)
    Slice<Friend> findByRequesterIdAndStatusOrderByIdDesc(Long requesterId, FriendStatus status, Pageable pageable);

    Slice<Friend> findByRequesterIdAndStatusAndIdLessThanOrderByIdDesc(
            Long requesterId, FriendStatus status, Long cursor, Pageable pageable);
}
