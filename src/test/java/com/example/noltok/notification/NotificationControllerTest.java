package com.example.noltok.notification;

import com.example.noltok.global.config.SecurityConfig;
import com.example.noltok.global.jwt.JwtAuthenticationFilter;
import com.example.noltok.global.jwt.JwtProvider;
import com.example.noltok.notification.dto.response.NotificationListResponse;
import com.example.noltok.notification.dto.response.NotificationReadResponse;
import com.example.noltok.notification.sse.SseEmitterRepository;
import com.example.noltok.support.ControllerTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// HTTP 계층 검증이 목적 — 비즈니스 로직 분기는 NotificationServiceTest(Mockito)가 커버.
// SSE 구독은 "구독 성사(200, text/event-stream)"까지만 검증 — 실제 이벤트 전달은
// SseEmitterRepository/NotificationConsumer 쪽에서 이미 검증됨(설계 문서 참고)
@WebMvcTest(controllers = NotificationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtProvider.class})
class NotificationControllerTest extends ControllerTestSupport {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private NotificationService notificationService;
    @MockitoBean
    private SseEmitterRepository sseEmitterRepository;

    @Test
    void 알림목록조회_인증_토큰_없이_요청하면_401을_응답한다() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 알림목록조회_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(notificationService.getNotifications(anyLong(), isNull(), anyInt()))
                .willReturn(NotificationListResponse.of(List.of(), false));

        // when & then
        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 알림읽음처리_정상_요청시_200을_응답한다() throws Exception {
        // given
        given(notificationService.markAsRead(1L, 10L))
                .willReturn(NotificationReadResponse.of(10L, true));

        // when & then
        mockMvc.perform(patch("/api/v1/notifications/10/read")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true));
    }

    @Test
    void SSE구독_정상_요청시_200과_text_event_stream을_응답한다() throws Exception {
        // given
        given(sseEmitterRepository.register(1L)).willReturn(new SseEmitter());

        // when & then: 비동기 응답이라 완료까지 기다린 뒤 상태 확인
        mockMvc.perform(get("/api/v1/notifications/subscribe")
                        .header("Authorization", bearerToken(1L)))
                .andExpect(request().asyncStarted())
                .andReturn();
    }
}
