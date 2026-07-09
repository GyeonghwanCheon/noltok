package com.example.noltok.auth;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends CrudRepository<RefreshToken, Long> {

    // 토큰 값으로 조회 (재발급 시 사용) — token 필드의 @Indexed 보조색인 이용
    Optional<RefreshToken> findByToken(String token);

    // userId로 조회 (로그인 시 기존 토큰 있으면 갱신) — @Id 기반이라 findById와 동일하지만
    // AuthService 호출부의 의미를 그대로 살리기 위해 이름 유지
    Optional<RefreshToken> findByUserId(Long userId);

    // 로그아웃 시 userId로 삭제
    // → deleteByUserId(파생 쿼리)는 시도해봤으나 Redis Repository에서
    //   실제로 삭제가 반영되지 않는 문제가 있어서(2026-07-09 발견),
    //   @Id가 곧 userId이므로 내장 deleteById()를 그대로 사용
    default void deleteByUserId(Long userId) {
        deleteById(userId);
    }
}
