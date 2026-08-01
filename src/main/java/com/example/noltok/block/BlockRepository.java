package com.example.noltok.block;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {

    // Block은 Friend와 달리 방향이 고정(blocker → blocked만 의미 있음)
    // → OR 조건 없이 단순 조회로 충분
    Optional<Block> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    // 차단 목록 조회용 — 커서 기반 페이지네이션 (Friend와 동일 패턴: id DESC, Slice)
    Slice<Block> findAllByBlockerIdAndIsActiveTrueOrderByIdDesc(Long blockerId, Pageable pageable);

    Slice<Block> findAllByBlockerIdAndIsActiveTrueAndIdLessThanOrderByIdDesc(
            Long blockerId, Long cursor, Pageable pageable);

    // 채팅방 초대 검증용
    // → "내가 상대를 차단" OR "상대가 나를 차단" 둘 다 확인해야 하므로 양방향 조회
    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM Block b WHERE " +
            "b.isActive = true AND " +
            "((b.blockerId = :userA AND b.blockedId = :userB) OR " +
            "(b.blockerId = :userB AND b.blockedId = :userA))")
    boolean existsActiveBlockBetween(@Param("userA") Long userA, @Param("userB") Long userB);

    // 채팅 메시지 파이프라인의 차단 필터링용 — BlockCacheService가 캐시 미스일 때만 호출
    // → 내가 차단한 사람 목록 (메시지 이력 조회 시 sender_id NOT IN 필터에 사용)
    @Query("SELECT b.blockedId FROM Block b WHERE b.blockerId = :blockerId AND b.isActive = true")
    List<Long> findBlockedIdsByBlockerIdAndIsActiveTrue(@Param("blockerId") Long blockerId);

    // → 나를 차단한 사람 목록 (실시간 브로드캐스트 수신 대상 필터링에 사용)
    @Query("SELECT b.blockerId FROM Block b WHERE b.blockedId = :blockedId AND b.isActive = true")
    List<Long> findBlockerIdsByBlockedIdAndIsActiveTrue(@Param("blockedId") Long blockedId);
}
