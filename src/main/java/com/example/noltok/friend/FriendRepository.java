package com.example.noltok.friend;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FriendRepository extends JpaRepository<Friend, Long> {

    // 단방향 저장이므로 requester/receiver 어느 쪽으로 저장돼 있어도 찾아야 함
    // → OR 조건으로 양방향 조회 (docs/decision-log.md 2026-06-26 결정)
    @Query("SELECT f FROM Friend f WHERE " +
            "(f.requesterId = :userA AND f.receiverId = :userB) OR " +
            "(f.requesterId = :userB AND f.receiverId = :userA)")
    Optional<Friend> findRelationBetween(@Param("userA") Long userA, @Param("userB") Long userB);

    // 친구 목록 조회용
    // → status=ACCEPTED이고, 내가 requester/receiver 어느 쪽이든 포함된 관계 전부 조회
    @Query("SELECT f FROM Friend f WHERE f.status = 'ACCEPTED' " +
            "AND (f.requesterId = :userId OR f.receiverId = :userId)")
    List<Friend> findAllAcceptedByUserId(@Param("userId") Long userId);
}
