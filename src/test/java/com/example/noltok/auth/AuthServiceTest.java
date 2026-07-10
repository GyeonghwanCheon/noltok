package com.example.noltok.auth;

import com.example.noltok.auth.dto.LoginRequest;
import com.example.noltok.auth.dto.LoginResponse;
import com.example.noltok.auth.dto.ReissueRequest;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.global.jwt.JwtProvider;
import com.example.noltok.global.jwt.TokenBlacklistService;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import com.example.noltok.user.dto.request.SignUpRequest;
import com.example.noltok.user.dto.response.SignUpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// DB/JWT 실제 서명 없이 순수 JVM에서 도는 단위 테스트 — JwtProvider도
// 실제 토큰을 만들지 않고 Mock으로 "이 userId를 반환한다"만 통제
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private JwtProvider jwtProvider;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenBlacklistService tokenBlacklistService;

    private AuthService authService;

    private static final long REFRESH_EXPIRATION = 604_800_000L; // 7일(ms)
    private final Long userId = 1L;

    private User testUser(Long id, String email, String encodedPassword, String nickname) {
        User user = User.create(email, encodedPassword, nickname);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.now());
        return user;
    }

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, refreshTokenRepository, jwtProvider, passwordEncoder, tokenBlacklistService);
        // @Value("${jwt.refresh-expiration}")는 Lombok 생성자에 안 잡히는 필드라
        // Spring 컨텍스트 없이 만든 인스턴스에는 리플렉션으로 직접 채워야 함
        ReflectionTestUtils.setField(authService, "refreshExpiration", REFRESH_EXPIRATION);
    }

    // ── signUp() ───────────────────────────────────────────────

    @Test
    void signUp_정상_가입시_비밀번호를_암호화해서_저장한다() {
        // given
        SignUpRequest request = new SignUpRequest("new@test.com", "password1", "닉네임");
        given(userRepository.existsByEmailAndIsActive("new@test.com", true)).willReturn(false);
        given(passwordEncoder.encode("password1")).willReturn("encoded-password");
        given(userRepository.save(any(User.class)))
                .willReturn(testUser(userId, "new@test.com", "encoded-password", "닉네임"));

        // when
        SignUpResponse response = authService.signUp(request);

        // then
        assertThat(response.email()).isEqualTo("new@test.com");
        assertThat(response.nickname()).isEqualTo("닉네임");
        verify(passwordEncoder).encode("password1");
    }

    @Test
    void signUp_이메일이_중복이면_예외이고_저장되지_않는다() {
        // given
        SignUpRequest request = new SignUpRequest("dup@test.com", "password1", "닉네임");
        given(userRepository.existsByEmailAndIsActive("dup@test.com", true)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        verify(userRepository, never()).save(any());
    }

    // ── login() ────────────────────────────────────────────────

    @Test
    void login_기존_RefreshToken이_없으면_새로_생성한다() {
        // given
        User user = testUser(userId, "user@test.com", "encoded-password", "유저");
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1", "encoded-password")).willReturn(true);
        given(jwtProvider.generateAccessToken(userId)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("refresh-token");
        given(refreshTokenRepository.findByUserId(userId)).willReturn(Optional.empty());

        // when
        LoginResponse response = authService.login(new LoginRequest("user@test.com", "password1"));

        // then
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void login_기존_RefreshToken이_있으면_rotate_후_save한다() {
        // given
        User user = testUser(userId, "user@test.com", "encoded-password", "유저");
        RefreshToken existing = RefreshToken.create(userId, "old-refresh-token", REFRESH_EXPIRATION);
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("password1", "encoded-password")).willReturn(true);
        given(jwtProvider.generateAccessToken(userId)).willReturn("access-token");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("new-refresh-token");
        given(refreshTokenRepository.findByUserId(userId)).willReturn(Optional.of(existing));

        // when
        authService.login(new LoginRequest("user@test.com", "password1"));

        // then: 새로 만들지 않고(rotate만) 기존 토큰이 갱신됨, Redis는 변경감지가 없어 save()도 호출돼야 함
        assertThat(existing.getToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void login_존재하지_않는_이메일이면_INVALID_CREDENTIALS() {
        // given
        given(userRepository.findByEmail("none@test.com")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.login(new LoginRequest("none@test.com", "password1")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void login_비밀번호가_틀리면_이메일_없을때와_동일한_INVALID_CREDENTIALS() {
        // given: 사용자 열거 공격 방지를 위해 이메일 오류와 같은 에러코드를 써야 함 (AuthService 주석 참고)
        User user = testUser(userId, "user@test.com", "encoded-password", "유저");
        given(userRepository.findByEmail("user@test.com")).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrong-password", "encoded-password")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> authService.login(new LoginRequest("user@test.com", "wrong-password")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    // ── reissue() ──────────────────────────────────────────────

    @Test
    void reissue_정상_재발급시_rotate_후_save한다() {
        // given
        RefreshToken existing = RefreshToken.create(userId, "old-refresh-token", REFRESH_EXPIRATION);
        given(refreshTokenRepository.findByToken("old-refresh-token")).willReturn(Optional.of(existing));
        given(jwtProvider.generateAccessToken(userId)).willReturn("new-access-token");
        given(jwtProvider.generateRefreshToken(userId)).willReturn("new-refresh-token");

        // when
        LoginResponse response = authService.reissue(new ReissueRequest("old-refresh-token"));

        // then
        assertThat(response.accessToken()).isEqualTo("new-access-token");
        assertThat(existing.getToken()).isEqualTo("new-refresh-token");
        verify(refreshTokenRepository).save(existing);
    }

    @Test
    void reissue_존재하지_않는_토큰이면_INVALID_REFRESH_TOKEN() {
        // given: 만료된 토큰은 Redis TTL로 이미 삭제돼 있어서 "없음"과 동일하게 처리됨
        given(refreshTokenRepository.findByToken("expired-or-unknown")).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> authService.reissue(new ReissueRequest("expired-or-unknown")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    // ── logout() ───────────────────────────────────────────────

    @Test
    void logout_RefreshToken을_삭제하고_AccessToken을_남은시간만큼_블랙리스트에_등록한다() {
        // given: 만료까지 30분(1_800_000ms) 남은 토큰
        Date expiration = new Date(System.currentTimeMillis() + 1_800_000L);
        given(jwtProvider.getExpiration("access-token")).willReturn(expiration);

        // when
        authService.logout(userId, "access-token");

        // then
        verify(refreshTokenRepository).deleteByUserId(userId);
        verify(tokenBlacklistService).blacklist(eq("access-token"), anyLong());
    }
}
