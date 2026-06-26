package com.example.noltok.auth;

import com.example.noltok.auth.dto.LoginRequest;
import com.example.noltok.auth.dto.LoginResponse;
import com.example.noltok.auth.dto.ReissueRequest;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.global.jwt.JwtProvider;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import com.example.noltok.user.dto.request.SignUpRequest;
import com.example.noltok.user.dto.response.SignUpResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;


    // 회원가입
    @Transactional
    public SignUpResponse signUp(SignUpRequest request) {

        validateDuplicateEmail(request.email());

        String encodedPassword = passwordEncoder.encode(request.password());

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

        // 2. 비밀번호 검증
        // 이메일이 없을 때와 같은 에러메시지 사용
        // 이유: "이메일이 없습니다" vs "비밀번호가 틀렸습니다"를 구분하면
        //       공격자가 가입된 이메일 여부를 알 수 있음 (사용자 열거 공격 방지)
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. 토큰 생성
        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        // 4. Refresh Token 저장 (기존 토큰 있으면 rotate, 없으면 새로 저장)
        refreshTokenRepository.findByUserId(user.getId())
                .ifPresentOrElse(
                        existing -> existing.rotate(refreshToken, refreshExpiration),
                        () -> refreshTokenRepository.save(
                                RefreshToken.create(user.getId(), refreshToken, refreshExpiration)
                        )
                );

        return LoginResponse.of(accessToken, refreshToken);
    }

    // 토큰 재발급
    @Transactional
    public LoginResponse reissue(ReissueRequest request) {

        // 1. Refresh Token으로 DB 조회
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(request.refreshToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        // 2. 만료 여부 확인
        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        // 3. 새 토큰 생성
        String newAccessToken = jwtProvider.generateAccessToken(refreshToken.getUserId());
        String newRefreshToken = jwtProvider.generateRefreshToken(refreshToken.getUserId());

        // 4. Refresh Token rotate
        refreshToken.rotate(newRefreshToken, refreshExpiration);

        return LoginResponse.of(newAccessToken, newRefreshToken);
    }

    // 로그아웃
    @Transactional
    public void logout(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }


    private void validateDuplicateEmail(String email) {
        if (userRepository.existsByEmailAndIsActive(email, true)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }
}
