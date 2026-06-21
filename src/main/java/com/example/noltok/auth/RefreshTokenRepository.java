package com.example.noltok.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // 토큰 값으로 조회 (재발급 시 사용)
    Optional<RefreshToken> findByToken(String token);

    // userId로 조회 (로그인 시 기존 토큰 있으면 갱신)
    Optional<RefreshToken> findByUserId(Long userId);

    // 로그아웃 시 userId로 삭제
    void deleteByUserId(Long userId);
}
