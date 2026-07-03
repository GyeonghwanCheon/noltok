package com.example.noltok.block;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {

    // Block은 Friend와 달리 방향이 고정(blocker → blocked만 의미 있음)
    // → OR 조건 없이 단순 조회로 충분
    Optional<Block> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // 차단 목록 조회용
    List<Block> findAllByBlockerIdAndIsActiveTrue(Long blockerId);

    // 채팅방 초대 검증용
    // → "내가 상대를 차단" OR "상대가 나를 차단" 둘 다 확인해야 하므로 양방향 조회
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Block b WHERE " +
            "b.isActive = true AND " +
            "((b.blockerId = :userA AND b.blockedId = :userB) OR " +
            "(b.blockerId = :userB AND b.blockedId = :userA))")
    boolean existsActiveBlockBetween(@Param("userA") Long userA, @Param("userB") Long userB);
}
