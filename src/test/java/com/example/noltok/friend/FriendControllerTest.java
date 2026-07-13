package com.example.noltok.friend;

import com.example.noltok.friend.dto.request.FriendRequestRequest;
import com.example.noltok.friend.dto.response.FriendAcceptResponse;
import com.example.noltok.friend.dto.response.FriendCancelResponse;
import com.example.noltok.friend.dto.response.FriendDeleteResponse;
import com.example.noltok.friend.dto.response.FriendListResponse;
import com.example.noltok.friend.dto.response.FriendReceivedListResponse;
import com.example.noltok.friend.dto.response.FriendRejectResponse;
import com.example.noltok.friend.dto.response.FriendRequestResponse;
import com.example.noltok.friend.dto.response.FriendSentListResponse;
import com.example.noltok.global.config.SecurityConfig;
import com.example.noltok.global.jwt.JwtAuthenticationFilter;
import com.example.noltok.global.jwt.JwtProvider;
import com.example.noltok.support.ControllerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HTTP 계층 검증이 목적 — 비즈니스 로직 분기는 FriendServiceTest(Mockito)가 커버
@WebMvcTest(controllers = FriendController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProvider.class})
class FriendControllerTest extends ControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private FriendService friendService;

    @Test
    void 친구요청_인증_토큰_없이_요청하면_401을_응답한다() throws Exception {
        mockMvc.perform(post("/api/v1/friends/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FriendRequestRequest("상대닉네임"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 친구요청_정상_요청시_201을_응답한다() throws Exception {
        // given
        given(friendService.sendRequest(anyLong(), any(FriendRequestRequest.class)))
                .willReturn(new FriendRequestResponse(1L, 2L, "상대닉네임", "PENDING", LocalDate.now()));

        // when & then
        mockMvc.perform(post("/api/v1/friends/request")
                        .header("Authorization", bearerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FriendRequestRequest("상대닉네임"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void 친구요청_닉네임이_비어있으면_400을_응답한다() throws Exception {
        // when & then
        mockMvc.perform(post("/api/v1/friends/request")
                        .header("Authorization", bearerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FriendRequestRequest(""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 친구요청_수락_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(friendService.acceptRequest(1L, 10L))
                .willReturn(new FriendAcceptResponse(10L, "상대닉네임", "ACCEPTED", "상대닉네임님과 친구가 되었습니다."));

        // when & then
        mockMvc.perform(patch("/api/v1/friends/10/accept")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));
    }

    @Test
    void 친구요청_거절_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(friendService.rejectRequest(1L, 10L))
                .willReturn(new FriendRejectResponse(10L, "REJECTED", "친구 요청을 거절하였습니다."));

        // when & then
        mockMvc.perform(patch("/api/v1/friends/10/reject")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void 친구목록조회_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(friendService.getFriends(1L)).willReturn(FriendListResponse.of(List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/friends")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 받은요청목록조회_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(friendService.getReceivedRequests(1L)).willReturn(FriendReceivedListResponse.of(List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/friends/received")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 보낸요청목록조회_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(friendService.getSentRequests(1L)).willReturn(FriendSentListResponse.of(List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/friends/sent")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 친구삭제_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(friendService.deleteFriend(1L, 10L))
                .willReturn(new FriendDeleteResponse(10L, "상대닉네임님을 친구에서 삭제했습니다."));

        // when & then
        mockMvc.perform(delete("/api/v1/friends/10")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friendId").value(10));
    }

    @Test
    void 친구요청취소_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(friendService.cancelRequest(1L, 10L))
                .willReturn(new FriendCancelResponse(10L, "상대닉네임님에게 보낸 친구 요청을 취소했습니다."));

        // when & then
        mockMvc.perform(delete("/api/v1/friends/10/cancel")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.friendId").value(10));
    }
}
