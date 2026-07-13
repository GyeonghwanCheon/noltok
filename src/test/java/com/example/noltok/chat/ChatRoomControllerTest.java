package com.example.noltok.chat;

import com.example.noltok.chat.dto.request.ChangeAdminRequest;
import com.example.noltok.chat.dto.request.CreateRoomRequest;
import com.example.noltok.chat.dto.request.InviteMembersRequest;
import com.example.noltok.chat.dto.response.ChatRoomAdminResponse;
import com.example.noltok.chat.dto.response.ChatRoomDeleteResponse;
import com.example.noltok.chat.dto.response.ChatRoomDetailResponse;
import com.example.noltok.chat.dto.response.ChatRoomInviteResponse;
import com.example.noltok.chat.dto.response.ChatRoomJoinResponse;
import com.example.noltok.chat.dto.response.ChatRoomKickResponse;
import com.example.noltok.chat.dto.response.ChatRoomLeaveResponse;
import com.example.noltok.chat.dto.response.ChatRoomListResponse;
import com.example.noltok.chat.dto.response.ChatRoomReadResponse;
import com.example.noltok.chat.dto.response.ChatRoomResponse;
import com.example.noltok.chat.dto.response.ChatRoomSearchResponse;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HTTP 계층 검증이 목적 — 비즈니스 로직 분기는 ChatRoomServiceTest(Mockito)가 커버
@WebMvcTest(controllers = ChatRoomController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProvider.class})
class ChatRoomControllerTest extends ControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private ChatRoomService chatRoomService;

    @Test
    void 채팅방생성_인증_토큰_없이_요청하면_401을_응답한다() throws Exception {
        CreateRoomRequest request = new CreateRoomRequest("방이름", ChatRoomType.GROUP, null, List.of("친구닉네임"));

        mockMvc.perform(post("/api/v1/chat/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 채팅방생성_정상_요청시_201을_응답한다() throws Exception {
        // given
        CreateRoomRequest request = new CreateRoomRequest("방이름", ChatRoomType.GROUP, null, List.of("친구닉네임"));
        ChatRoom room = ChatRoom.create("방이름", ChatRoomType.GROUP, 1L, null);
        ReflectionTestUtils.setField(room, "id", 1L);
        ReflectionTestUtils.setField(room, "createdAt", LocalDateTime.now());
        given(chatRoomService.createRoom(anyLong(), any(CreateRoomRequest.class)))
                .willReturn(ChatRoomResponse.of(room, 2, ChatRoomRole.ADMIN));

        // when & then
        mockMvc.perform(post("/api/v1/chat/rooms")
                        .header("Authorization", bearerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.roomname").value("방이름"));
    }

    @Test
    void 채팅방생성_타입이_없으면_400을_응답한다() throws Exception {
        // given: @NotNull(type) 검증 실패 유도 — JSON에서 type 필드 자체를 생략
        String requestJson = "{\"roomname\":\"방이름\",\"nicknames\":[\"친구닉네임\"]}";

        // when & then
        mockMvc.perform(post("/api/v1/chat/rooms")
                        .header("Authorization", bearerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void 내채팅방목록조회_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(chatRoomService.getMyRooms(1L)).willReturn(ChatRoomListResponse.of(List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/chat/rooms")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 채팅방검색_정상_요청시_200을_응답한다() throws Exception {
        // given: 검색 API는 @AuthenticationPrincipal이 없지만, permitAll 목록엔
        // 없으므로 SecurityConfig에 의해 여전히 인증은 필요함
        given(chatRoomService.searchRooms("개발")).willReturn(ChatRoomSearchResponse.of(List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/chat/rooms/search")
                        .header("Authorization", bearerToken(1L))
                        .param("name", "개발"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 채팅방상세조회_정상_요청시_200을_응답한다() throws Exception {
        // given
        ChatRoom room = ChatRoom.create("방이름", ChatRoomType.GROUP, 1L, null);
        ReflectionTestUtils.setField(room, "id", 1L);
        ReflectionTestUtils.setField(room, "createdAt", LocalDateTime.now());
        ChatRoomMember member = ChatRoomMember.create(room, 1L, ChatRoomRole.ADMIN);
        given(chatRoomService.getRoomDetail(1L, 1L))
                .willReturn(ChatRoomDetailResponse.of(room, member, List.of()));

        // when & then
        mockMvc.perform(get("/api/v1/chat/rooms/1")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomname").value("방이름"));
    }

    @Test
    void 채팅방입장_정상_요청시_200을_응답한다() throws Exception {
        // given: JoinRoomRequest 바디 없이(OPEN 타입) 입장하는 케이스
        given(chatRoomService.joinRoom(eq(1L), eq(1L), isNull()))
                .willReturn(ChatRoomJoinResponse.of(1L, "MEMBER", "닉네임"));

        // when & then
        mockMvc.perform(post("/api/v1/chat/rooms/1/join")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRole").value("MEMBER"));
    }

    @Test
    void 채팅방멤버초대_정상_요청시_200을_응답한다() throws Exception {
        // given
        InviteMembersRequest request = new InviteMembersRequest(List.of("초대할닉네임"));
        given(chatRoomService.inviteMembers(eq(1L), eq(1L), any(InviteMembersRequest.class)))
                .willReturn(ChatRoomInviteResponse.of(1L, List.of(), List.of("초대할닉네임")));

        // when & then
        mockMvc.perform(post("/api/v1/chat/rooms/1/members")
                        .header("Authorization", bearerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(1));
    }

    @Test
    void 채팅방멤버추방_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(chatRoomService.kickMember(1L, 1L, 2L))
                .willReturn(ChatRoomKickResponse.of(1L, 2L, "추방된닉네임"));

        // when & then
        mockMvc.perform(delete("/api/v1/chat/rooms/1/members/2")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kickedUserId").value(2));
    }

    @Test
    void 채팅방관리자변경_정상_요청시_200을_응답한다() throws Exception {
        // given
        ChangeAdminRequest request = new ChangeAdminRequest(2L);
        given(chatRoomService.changeAdmin(eq(1L), eq(1L), any(ChangeAdminRequest.class)))
                .willReturn(ChatRoomAdminResponse.of(1L, 1L, 2L, "기존관리자", "새관리자"));

        // when & then
        mockMvc.perform(patch("/api/v1/chat/rooms/1/admin")
                        .header("Authorization", bearerToken(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.newAdminUserId").value(2));
    }

    @Test
    void 채팅방나가기_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(chatRoomService.leaveRoom(1L, 1L)).willReturn(ChatRoomLeaveResponse.of(1L, "방이름"));

        // when & then
        mockMvc.perform(delete("/api/v1/chat/rooms/1/leave")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(1));
    }

    @Test
    void 채팅방삭제_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(chatRoomService.deleteRoom(1L, 1L)).willReturn(ChatRoomDeleteResponse.of(1L, "방이름"));

        // when & then
        mockMvc.perform(delete("/api/v1/chat/rooms/1")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(1));
    }

    @Test
    void 채팅방읽음처리_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(chatRoomService.markAsRead(1L, 1L)).willReturn(ChatRoomReadResponse.of(1L, 100L));

        // when & then
        mockMvc.perform(patch("/api/v1/chat/rooms/1/read")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastReadMessageId").value(100));
    }
}
