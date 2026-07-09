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
        // → Redis는 JPA 변경감지가 없어서 rotate() 후 save()를 명시적으로 호출해야 함
        refreshTokenRepository.findByUserId(user.getId())
                .ifPresentOrElse(
                        existing -> {
                            existing.rotate(refreshToken, refreshExpiration);
                            refreshTokenRepository.save(existing);
                        },
                        () -> refreshTokenRepository.save(
                                RefreshToken.create(user.getId(), refreshToken, refreshExpiration)
                        )
                );

        return LoginResponse.of(accessToken, refreshToken);
    }

    // 토큰 재발급
    @Transactional
    public LoginResponse reissue(ReissueRequest request) {

        // 1. Refresh Token으로 조회
        // → 만료된 토큰은 Redis TTL에 의해 이미 삭제된 상태라 여기서 자연히 걸러짐
        //   (별도의 만료 여부 수동 체크가 필요 없음)
        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(request.refreshToken())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        // 2. 새 토큰 생성
        String newAccessToken = jwtProvider.generateAccessToken(refreshToken.getUserId());
        String newRefreshToken = jwtProvider.generateRefreshToken(refreshToken.getUserId());

        // 3. Refresh Token rotate → Redis는 변경감지가 없으므로 save()로 명시적 반영
        refreshToken.rotate(newRefreshToken, refreshExpiration);
        refreshTokenRepository.save(refreshToken);

        return LoginResponse.of(newAccessToken, newRefreshToken);
    }

    // 로그아웃
    @Transactional
    public void logout(Long userId, String accessToken) {
        // 1. Refresh Token 삭제
        refreshTokenRepository.deleteByUserId(userId);

        // 2. Access Token은 자체 만료까지 남은 시간만큼만 블랙리스트에 등록
        //    (그 이상 남겨둘 필요 없음 — 이미 만료됐어야 할 시점 이후엔 어차피 무효)
        long remainingMillis = jwtProvider.getExpiration(accessToken).getTime() - System.currentTimeMillis();
        tokenBlacklistService.blacklist(accessToken, remainingMillis);
    }


    private void validateDuplicateEmail(String email) {
        if (userRepository.existsByEmailAndIsActive(email, true)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
    }
}
