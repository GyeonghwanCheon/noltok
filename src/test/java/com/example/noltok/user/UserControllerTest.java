package com.example.noltok.user;

import com.example.noltok.global.config.SecurityConfig;
import com.example.noltok.global.jwt.JwtAuthenticationFilter;
import com.example.noltok.global.jwt.JwtProvider;
import com.example.noltok.support.ControllerTestSupport;
import com.example.noltok.user.dto.request.ChangePasswordRequest;
import com.example.noltok.user.dto.request.UpdateProfileRequest;
import com.example.noltok.user.dto.response.DeleteAccountResponse;
import com.example.noltok.user.dto.response.UserResponse;
import com.example.noltok.user.dto.response.UserSummaryResponse;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HTTP 계층(요청 매핑/검증/인증/예외→상태코드 변환) 검증이 목적 — 비즈니스
// 로직 분기는 이미 UserServiceTest(Mockito)가 커버 중이라 UserService는 Mock
@WebMvcTest(controllers = UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProvider.class})
class UserControllerTest extends ControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private UserService userService;

    @Test
    void 내정보조회_인증_토큰_없이_요청하면_401을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 내정보조회_정상_요청시_200을_응답한다() throws Exception {
        // given
        UserResponse response = new UserResponse(1L, "user@test.com", "닉네임", null, LocalDate.now());
        given(userService.getMyInfo(1L)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("닉네임"));
    }

    @Test
    void 회원정보수정_정상_요청시_200을_응답한다() throws Exception {
        // given
        UpdateProfileRequest request = new UpdateProfileRequest("새닉네임", null);
        UserResponse response = new UserResponse(1L, "user@test.com", "새닉네임", null, LocalDate.now());
        given(userService.updateMyInfo(eq(1L), any(UpdateProfileRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", bearerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("새닉네임"));
    }

    @Test
    void 회원정보수정_닉네임이_1자면_400을_응답한다() throws Exception {
        // given: @Size(min=2) 검증 실패 유도
        UpdateProfileRequest request = new UpdateProfileRequest("a", null);

        // when & then
        mockMvc.perform(patch("/api/v1/users/me")
                        .header("Authorization", bearerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 비밀번호변경_정상_요청시_200을_응답한다() throws Exception {
        // given
        ChangePasswordRequest request = new ChangePasswordRequest("current1", "newpass1", "newpass1");

        // when & then
        mockMvc.perform(patch("/api/v1/users/me/password")
                        .header("Authorization", bearerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 유저검색_정상_요청시_200을_응답한다() throws Exception {
        // given
        UserSummaryResponse summary = new UserSummaryResponse(2L, "검색된닉네임", null);
        given(userService.searchUsers(eq("검색"), anyLong())).willReturn(java.util.List.of(summary));

        // when & then
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", bearerToken(1L))
                        .param("nickname", "검색"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].nickname").value("검색된닉네임"));
    }

    @Test
    void 유저상세조회_정상_요청시_200을_응답한다() throws Exception {
        // given: 이 API는 @AuthenticationPrincipal이 없어 인증 없이도 통과해야 함 —
        // 단, SecurityConfig의 permitAll 목록엔 없으므로 실제로는 토큰이 필요함
        UserSummaryResponse summary = new UserSummaryResponse(2L, "상대닉네임", null);
        given(userService.getUserDetail(2L)).willReturn(summary);

        // when & then
        mockMvc.perform(get("/api/v1/users/2")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("상대닉네임"));
    }

    @Test
    void 회원탈퇴_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(userService.deleteMyAccount(1L)).willReturn(new DeleteAccountResponse(1L));

        // when & then
        mockMvc.perform(delete("/api/v1/users/me")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1));
    }
}
