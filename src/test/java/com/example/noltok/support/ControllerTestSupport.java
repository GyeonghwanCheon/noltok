package com.example.noltok.support;

import com.example.noltok.global.jwt.JwtProvider;
import com.example.noltok.global.jwt.TokenBlacklistService;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.BDDMockito.given;

// Controller(@WebMvcTest) 테스트 공통 베이스
// → 실제 SecurityConfig+JwtAuthenticationFilter+JwtProvider를 그대로 태워서
//   "인증됐다고 가정"이 아니라 진짜 서명된 토큰으로 인증 흐름 자체를 검증함
// → UserRepository/TokenBlacklistService는 JPA/Redis 실제 연결이 필요 없어
//   MockitoBean으로 대체 (JwtAuthenticationFilter/JwtProvider의 생성자 의존성)
public abstract class ControllerTestSupport {

    @Autowired
    protected JwtProvider jwtProvider;

    @MockitoBean
    protected UserRepository userRepository;
    @MockitoBean
    protected TokenBlacklistService tokenBlacklistService;

    // 실제로 존재하는 유저인 것처럼 JwtAuthenticationFilter가 인식하도록
    // Mock을 설정한 뒤, 그 userId로 진짜 서명된 Access Token을 발급해
    // Authorization 헤더 값으로 반환
    protected String bearerToken(Long userId) {
        User user = User.create("user" + userId + "@test.com", "encoded-password", "유저" + userId);
        ReflectionTestUtils.setField(user, "id", userId);
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        return "Bearer " + jwtProvider.generateAccessToken(userId);
    }
}
