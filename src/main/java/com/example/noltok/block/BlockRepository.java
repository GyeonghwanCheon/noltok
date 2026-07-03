package com.example.noltok.block;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BlockRepository extends JpaRepository<Block, Long> {

    // Block은 Friend와 달리 방향이 고정(blocker → blocked만 의미 있음)
    // → OR 조건 없이 단순 조회로 충분
    Optional<Block> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);
}
