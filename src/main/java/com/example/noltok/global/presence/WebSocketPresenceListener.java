package com.example.noltok.global.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

// STOMP DISCONNECT 대신 Spring 세션 이벤트 사용 — 브라우저를 그냥 닫아도 항상 발행됨
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
