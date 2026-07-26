package com.example.noltok.global.ratelimit;

public enum RateLimitKeyType {
    IP,             // 인증 전 API (로그인 등) — 요청자 IP로 식별
    USER_AND_ROOM,  // 인증 후, 특정 방을 대상으로 한 API (채팅방 입장 등) — userId+roomId로 식별
}
