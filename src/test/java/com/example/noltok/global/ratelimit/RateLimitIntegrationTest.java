package com.example.noltok.global.ratelimit;

import com.example.noltok.auth.AuthController;
import com.example.noltok.auth.AuthService;
import com.example.noltok.auth.dto.LoginRequest;
import com.example.noltok.chat.ChatRoomController;
import com.example.noltok.chat.ChatRoomService;
import com.example.noltok.chat.ChatRoomType;
import com.example.noltok.chat.dto.request.CreateRoomRequest;
import com.example.noltok.chat.dto.request.JoinRoomRequest;
import com.example.noltok.chat.dto.response.ChatRoomResponse;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.support.AbstractIntegrationTest;
import com.example.noltok.user.dto.request.SignUpRequest;
import com.example.noltok.user.dto.response.SignUpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Rate Limiting(로그인 IP 기준, 채팅방 입장 userId+roomId 기준)이 실제 Redis 카운터로
// 정확히 동작하는지 검증하는 통합 테스트. Controller 빈을 직접 호출해서 AOP 프록시
// (@RateLimited → RateLimitAspect)가 실제로 걸려있는지까지 함께 검증한다
// (Service를 직접 호출하면 어노테이션이 안 걸려있는 채로도 테스트가 통과해버림).
class RateLimitIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private RateLimiterService rateLimiterService;
    @Autowired
    private AuthController authController;
    @Autowired
    private AuthService authService;
    @Autowired
    private ChatRoomController chatRoomController;
    @Autowired
    private ChatRoomService chatRoomService;

    @Test
    void RateLimiterService는_limit을_넘으면_실제_Redis_기준으로_거부한다() {
        // given
        String key = "rate_limit:test:" + UUID.randomUUID();

        // when & then: 5회까지는 허용
        for (int i = 1; i <= 5; i++) {
            assertThat(rateLimiterService.tryConsume(key, 5, 60)).isTrue();
        }
        // 6번째는 거부
        assertThat(rateLimiterService.tryConsume(key, 5, 60)).isFalse();
    }

    @Test
    void 로그인_API에_같은_IP로_6번_연속_요청하면_6번째부터_RATE_LIMIT_EXCEEDED() {
        // given: RateLimitAspect가 IP를 꺼내오는 RequestContextHolder를 직접 세팅
        // (Controller를 HTTP 없이 직접 호출하는 테스트라 실제 요청 컨텍스트가 없음)
        MockHttpServletRequest mockRequest = new MockHttpServletRequest();
        mockRequest.setRemoteAddr("203.0.113.10");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));

        try {
            LoginRequest badLogin = new LoginRequest("no-such-user-ratelimit-test@noltok.com", "wrongpassword");

            // when & then: 1~5회는 정상적인 인증 실패(401)
            for (int i = 1; i <= 5; i++) {
                assertThatThrownBy(() -> authController.login(badLogin))
                        .isInstanceOf(BusinessException.class)
                        .extracting(e -> ((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
            }

            // 6번째부터는 Rate Limit
            assertThatThrownBy(() -> authController.login(badLogin))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    void 채팅방_입장_API에_같은_유저_같은_방으로_6번_연속_요청하면_6번째부터_RATE_LIMIT_EXCEEDED() {
        // given: OPEN_PRIVATE 방 하나와, 그 방에 아직 안 들어간 유저 하나
        SignUpResponse owner = authService.signUp(
                new SignUpRequest("ratelimit-room-owner@noltok.com", "password1", "방장"));
        SignUpResponse joiner = authService.signUp(
                new SignUpRequest("ratelimit-room-joiner@noltok.com", "password1", "입장시도자"));

        ChatRoomResponse room = chatRoomService.createRoom(owner.userId(),
                new CreateRoomRequest("레이트리밋테스트방", ChatRoomType.OPEN_PRIVATE, "correct-password", null));

        UserDetails joinerDetails = User.builder()
                .username(String.valueOf(joiner.userId()))
                .password("")
                .roles("USER")
                .build();
        JoinRoomRequest wrongPassword = new JoinRoomRequest("wrong-password");

        // when & then: 1~5회는 비밀번호 불일치(403)
        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> chatRoomController.joinRoom(joinerDetails, room.roomId(), wrongPassword))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CHATROOM_PASSWORD);
        }

        // 6번째부터는 Rate Limit
        assertThatThrownBy(() -> chatRoomController.joinRoom(joinerDetails, room.roomId(), wrongPassword))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    @Test
    void 채팅방이_다르면_카운터도_별도로_취급된다() {
        // given: 같은 유저가 서로 다른 두 방에 각각 5번씩 시도 — 서로의 카운트에 영향 없어야 함
        SignUpResponse owner = authService.signUp(
                new SignUpRequest("ratelimit-multiroom-owner@noltok.com", "password1", "방장2"));
        SignUpResponse joiner = authService.signUp(
                new SignUpRequest("ratelimit-multiroom-joiner@noltok.com", "password1", "입장시도자2"));

        ChatRoomResponse roomA = chatRoomService.createRoom(owner.userId(),
                new CreateRoomRequest("방A", ChatRoomType.OPEN_PRIVATE, "correct-password", null));
        ChatRoomResponse roomB = chatRoomService.createRoom(owner.userId(),
                new CreateRoomRequest("방B", ChatRoomType.OPEN_PRIVATE, "correct-password", null));

        UserDetails joinerDetails = User.builder()
                .username(String.valueOf(joiner.userId()))
                .password("")
                .roles("USER")
                .build();
        JoinRoomRequest wrongPassword = new JoinRoomRequest("wrong-password");

        // when: 방A에 5번 소진
        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> chatRoomController.joinRoom(joinerDetails, roomA.roomId(), wrongPassword))
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INVALID_CHATROOM_PASSWORD);
        }

        // then: 방B는 별도 카운터라 여전히 정상적으로 비밀번호 검증까지 도달함
        assertThatThrownBy(() -> chatRoomController.joinRoom(joinerDetails, roomB.roomId(), wrongPassword))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CHATROOM_PASSWORD);
    }
}
