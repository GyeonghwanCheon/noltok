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
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;
    private final TokenBlacklistService tokenBlacklistService;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;


    // 회원가입
    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {

        // 1. 이메일 중복 확인
        validateDuplicateEmail(request.email());

        // 2. 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(request.password());

        // 3. User 생성 및 저장
        User user = User.create(request.email(), encodedPassword, request.nickname());
        User savedUser = userRepository.save(user);

        return SignUpResponse.from(savedUser);
    }

    // 로그인
    @Transactional
    public LoginResponse login(LoginRequest request) {

        // 1. 이메일로 User 조회
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 2. 비밀번호 검증 (이메일 오류와 동일한 메시지 — 사용자 열거 공격 방지)
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. 토큰 생성
        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        // 4. Refresh Token 저장 — 기존 토큰이 있으면 삭제 후 새로 저장
        refreshTokenRepository.deleteById(user.getId());
        refreshTokenRepository.save(RefreshToken.create(user.getId(), refreshToken, refreshExpiration));

        return LoginResponse.of(accessToken, refreshToken);
    }

    // 토큰 재발급
    @Transactional
    public LoginResponse reissue(ReissueRequest request) {

        // 1. Refresh Token으로 조회 (만료된 토큰은 Redis TTL로 이미 삭제됨)
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(request.refreshToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        // 2. 새 토큰 생성
        Long userId = refreshToken.getUserId();
        String newAccessToken = jwtProvider.generateAccessToken(userId);
        String newRefreshToken = jwtProvider.generateRefreshToken(userId);

        // 3. Refresh Token 삭제 후 재생성 (login()과 동일한 이유)
        refreshTokenRepository.deleteById(userId);
        refreshTokenRepository.save(RefreshToken.create(userId, newRefreshToken, refreshExpiration));

        return LoginResponse.of(newAccessToken, newRefreshToken);
    }

    // 로그아웃
    @Transactional
    public void logout(Long userId, String accessToken) {
        // 1. Refresh Token 삭제
        refreshTokenRepository.deleteByUserId(userId);

        // 2. Access Token은 자체 만료까지 남은 시간만큼만 블랙리스트에 등록
        long remainingMillis = jwtProvider.getExpiration(accessToken).getTime() - System.currentTimeMillis();
        tokenBlacklistService.blacklist(accessToken, remainingMillis);
    }


    private void validateDuplicateEmail(String email) {
        if (userRepository.existsByEmailAndIsActive(email, true)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }
}
