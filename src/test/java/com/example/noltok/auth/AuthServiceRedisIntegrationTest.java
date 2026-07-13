package com.example.noltok.auth;

import com.example.noltok.auth.dto.LoginRequest;
import com.example.noltok.auth.dto.LoginResponse;
import com.example.noltok.auth.dto.ReissueRequest;
import com.example.noltok.global.jwt.TokenBlacklistService;
import com.example.noltok.support.AbstractIntegrationTest;
import com.example.noltok.user.dto.request.SignUpRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Refresh Token 저장소가 Redis로 이전된 뒤(Phase 7) 실제 Redis를 대고
// 검증하는 통합 테스트 — RefreshTokenRepository.deleteByUserId()가
// 실제로는 삭제를 안 하던 버그(troubleshooting-log.md 2026-07-09)를
// 이 테스트가 있었다면 자동으로 잡았을 것이라 회귀 방지 목적으로 추가
class AuthServiceRedisIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuthService authService;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private TokenBlacklistService tokenBlacklistService;

    @Test
    void 로그인시_실제_Redis에_RefreshToken이_저장된다() {
        // given
        authService.signUp(new SignUpRequest("redistest1@test.com", "password1", "레디스테스트1"));

        // when
        LoginResponse response = authService.login(new LoginRequest("redistest1@test.com", "password1"));

        // then: findByUserId는 이메일이 아니라 userId가 필요한데, 응답엔 없으므로
        // token 값으로 실제 Redis에서 찾아지는지 확인
        Optional<RefreshToken> saved = refreshTokenRepository.findByToken(response.refreshToken());
        assertThat(saved).isPresent();
    }

    @Test
    void 재발급시_기존_토큰은_무효화되고_새_토큰으로_교체된다() {
        // given
        authService.signUp(new SignUpRequest("redistest2@test.com", "password1", "레디스테스트2"));
        LoginResponse loginResponse = authService.login(new LoginRequest("redistest2@test.com", "password1"));

        // when
        LoginResponse reissueResponse = authService.reissue(new ReissueRequest(loginResponse.refreshToken()));

        // then: 기존 토큰으론 더 이상 못 찾고, 새 토큰으로만 찾아짐 (실제 Redis rotate 검증)
        assertThat(refreshTokenRepository.findByToken(loginResponse.refreshToken())).isEmpty();
        assertThat(refreshTokenRepository.findByToken(reissueResponse.refreshToken())).isPresent();
    }

    @Test
    void 로그아웃시_실제_Redis에서_RefreshToken이_삭제되고_AccessToken이_블랙리스트에_등록된다() {
        // given
        authService.signUp(new SignUpRequest("redistest3@test.com", "password1", "레디스테스트3"));
        LoginResponse loginResponse = authService.login(new LoginRequest("redistest3@test.com", "password1"));
        Long userId = refreshTokenRepository.findByToken(loginResponse.refreshToken())
                .orElseThrow().getUserId();

        // when
        authService.logout(userId, loginResponse.accessToken());

        // then: deleteByUserId()가 실제로 삭제를 반영하는지 (파생 쿼리 무동작 버그 회귀 방지)
        assertThat(refreshTokenRepository.findByUserId(userId)).isEmpty();
        assertThat(tokenBlacklistService.isBlacklisted(loginResponse.accessToken())).isTrue();
    }
}
