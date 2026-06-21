package com.example.noltok.auth.dto;

public record LoginResponse(

        String accessToken,
        String refreshToken,
        String tokenType

) {
    // 정적 팩토리 메서드로 tokenType "Bearer" 고정
    public static LoginResponse of(String accessToken, String refreshToken) {
        return new LoginResponse(accessToken, refreshToken, "Bearer");
    }
}
