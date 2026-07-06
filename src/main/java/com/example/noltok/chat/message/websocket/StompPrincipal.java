package com.example.noltok.chat.message.websocket;

import java.security.Principal;

// STOMP 세션에 인증된 userId를 담기 위한 최소 구현
// → REST의 UserDetails.getUsername()과 동일하게 getName()이 userId 문자열을 반환
public class StompPrincipal implements Principal {

    private final String userId;

    public StompPrincipal(Long userId) {
        this.userId = String.valueOf(userId);
    }

    @Override
    public String getName() {
        return userId;
    }
}
