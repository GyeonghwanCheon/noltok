package com.example.noltok.auth;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {

    // 토큰 값으로 조회 (재발급 시 사용) — token 필드의 @Indexed 보조색인 이용
    Optional<RefreshToken> findByToken(String token);

    // userId로 조회 (로그인 시 기존 토큰 있으면 갱신)
    Optional<RefreshToken> findByUserId(Long userId);

    // 로그아웃 시 userId로 삭제 — 파생 쿼리(deleteByUserId)는 Redis Repository에서
    // 실제로 삭제가 반영되지 않는 버그가 있어 deleteById()로 대체
    default void deleteByUserId(Long userId) {
        deleteById(userId);
    }
}
