package com.example.noltok.auth;

import com.example.noltok.auth.dto.LoginRequest;
import com.example.noltok.auth.dto.LoginResponse;
import com.example.noltok.auth.dto.ReissueRequest;
import com.example.noltok.global.config.SecurityConfig;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.global.jwt.JwtAuthenticationFilter;
import com.example.noltok.global.jwt.JwtProvider;
import com.example.noltok.support.ControllerTestSupport;
import com.example.noltok.user.dto.request.SignUpRequest;
import com.example.noltok.user.dto.response.SignUpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HTTP 계층(요청 매핑/검증/인증/예외→상태코드 변환) 검증이 목적 — 비즈니스
// 로직 분기는 이미 AuthServiceTest(Mockito)가 커버 중이라 AuthService는 Mock
@WebMvcTest(controllers = AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProvider.class})
class AuthControllerTest extends ControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;
    // com.fasterxml.jackson.databind.ObjectMapper(Jackson 2)가 아니라
    // tools.jackson.databind.ObjectMapper(Jackson 3) — Boot 4.1.0이 Jackson 3
    // 기준으로 자동 설정하는 빈 타입이 이것 (docs/troubleshooting-log.md 참고)
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private AuthService authService;

    @Test
    void 회원가입_정상_요청시_201과_생성된_정보를_응답한다() throws Exception {
        // given
        SignUpRequest request = new SignUpRequest("new@test.com", "password1", "닉네임");
        SignUpResponse response = new SignUpResponse(1L, "new@test.com", "닉네임", LocalDate.now());
        given(authService.signUp(any(SignUpRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("new@test.com"));
    }

    @Test
    void 회원가입_이메일_형식이_틀리면_400을_응답한다() throws Exception {
        // given: @Email 검증 실패 유도, permitAll 경로라 인증 없이도 여기까지 도달해야 함
        SignUpRequest request = new SignUpRequest("invalid-email", "password1", "닉네임");

        // when & then
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 로그인_정상_요청시_200과_토큰을_응답한다() throws Exception {
        // given
        LoginRequest request = new LoginRequest("user@test.com", "password1");
        given(authService.login(any(LoginRequest.class)))
                .willReturn(LoginResponse.of("access-token", "refresh-token"));

        // when & then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"));
    }

    @Test
    void 로그인_실패시_Service의_BusinessException이_401로_변환된다() throws Exception {
        // given: GlobalExceptionHandler가 ErrorCode.getHttpStatus()를 그대로 사용하는지 검증
        LoginRequest request = new LoginRequest("user@test.com", "wrong-password");
        willThrow(new BusinessException(ErrorCode.INVALID_CREDENTIALS))
                .given(authService).login(any(LoginRequest.class));

        // when & then
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_CREDENTIALS.getMessage()));
    }

    @Test
    void 재발급_정상_요청시_200과_새_토큰을_응답한다() throws Exception {
        // given
        ReissueRequest request = new ReissueRequest("old-refresh-token");
        given(authService.reissue(any(ReissueRequest.class)))
                .willReturn(LoginResponse.of("new-access-token", "new-refresh-token"));

        // when & then
        mockMvc.perform(post("/api/v1/auth/reissue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshToken").value("new-refresh-token"));
    }

    @Test
    void 로그아웃_인증_토큰_없이_요청하면_401을_응답한다() throws Exception {
        // when & then: /logout은 permitAll 목록에 없으므로 SecurityConfig가 차단해야 함
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 로그아웃_정상_토큰으로_요청하면_200을_응답한다() throws Exception {
        // when & then: 실제 서명된 토큰으로 JwtAuthenticationFilter 인증 통과까지 검증
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}
