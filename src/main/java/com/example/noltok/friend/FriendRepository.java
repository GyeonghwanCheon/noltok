package com.example.noltok.friend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    // 단방향 저장이라 OR 조건으로 양방향 조회
    @Query("SELECT f FROM Friend f WHERE " +
            "(f.requesterId = :userA AND f.receiverId = :userB) OR " +
            "(f.requesterId = :userB AND f.receiverId = :userA)")
    Optional<Friend> findRelationBetween(@Param("userA") Long userA, @Param("userB") Long userB);

    // status=ACCEPTED, 내가 requester/receiver 어느 쪽이든 포함된 관계 전부 조회
    @Query("SELECT f FROM Friend f WHERE f.status = 'ACCEPTED' " +
            "AND (f.requesterId = :userId OR f.receiverId = :userId)")
    List<Friend> findAllAcceptedByUserId(@Param("userId") Long userId);

    // 방향 고정(receiverId)이라 단순 쿼리로 충분
    List<Friend> findAllByReceiverIdAndStatus(Long receiverId, FriendStatus status);

    // findAllByReceiverIdAndStatus()와 대칭 (방향만 requesterId)
    List<Friend> findAllByRequesterIdAndStatus(Long requesterId, FriendStatus status);
}
