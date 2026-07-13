package com.example.noltok.chat.message;

import com.example.noltok.chat.message.dto.response.ChatMessageListResponse;
import com.example.noltok.global.config.SecurityConfig;
import com.example.noltok.global.jwt.JwtAuthenticationFilter;
import com.example.noltok.global.jwt.JwtProvider;
import com.example.noltok.support.ControllerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// GET /api/v1/chat/rooms/{roomId}/messages (REST)만 대상 — STOMP @MessageMapping은
// 일반 HTTP 엔드포인트가 아니라 MockMvc로 검증 불가(설계 문서 참고),
// 해당 로직은 ChatMessageConsumerIntegrationTest가 실제 Kafka로 간접 커버 중
@WebMvcTest(controllers = ChatMessageController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProvider.class})
class ChatMessageControllerTest extends ControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ChatMessageService chatMessageService;
    // ChatMessageController가 STOMP sendMessage()에서 사용하는 의존성 —
    // 이 테스트 대상은 아니지만 컨트롤러 빈 생성을 위해 필요
    @MockitoBean
    private SimpMessagingTemplate simpMessagingTemplate;

    @Test
    void 메시지목록조회_인증_토큰_없이_요청하면_401을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/chat/rooms/1/messages"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 메시지목록조회_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(chatMessageService.getMessages(anyLong(), anyLong(), isNull(), anyInt()))
                .willReturn(ChatMessageListResponse.of(List.of(), false));

        // when & then
        mockMvc.perform(get("/api/v1/chat/rooms/1/messages")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }
}
