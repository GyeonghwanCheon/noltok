package com.example.noltok.global.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

// STOMP DISCONNECT 프레임이 아닌 Spring의 세션 이벤트를 사용
// → 브라우저를 그냥 닫거나 네트워크가 끊기는 등 클라이언트가 DISCONNECT
//   프레임을 보낼 기회가 없는 경우에도 SessionDisconnectEvent는 항상 발행됨
@Component
@RequiredArgsConstructor
public class WebSocketPresenceListener {

    private final UserPresenceService userPresenceService;

    @EventListener
    public void handleConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = extractUserId(event.getUser());
        if (userId != null) {
            userPresenceService.connect(userId, accessor.getSessionId());
        }
    }

    @EventListener
    public void handleDisconnected(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Long userId = extractUserId(event.getUser());
        if (userId != null) {
            userPresenceService.disconnect(userId, accessor.getSessionId());
        }
    }

    private Long extractUserId(Principal principal) {
        if (principal == null) {
            return null;
        }
        return Long.parseLong(principal.getName());
    }
}
