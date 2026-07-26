package com.example.noltok.global.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimited {

    String action();               // Redis 키 네임스페이스 (예: "login", "room-join")

    RateLimitKeyType keyType();     // 식별자 기준

    int limit();                    // windowSeconds 안에 허용할 최대 횟수

    int windowSeconds();             // 카운트 윈도우 길이(초)
}
