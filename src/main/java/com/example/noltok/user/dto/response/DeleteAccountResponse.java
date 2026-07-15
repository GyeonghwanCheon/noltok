package com.example.noltok.user.dto.response;

public record DeleteAccountResponse(
        Long userId
        // message는 Controller의 ApiResponse에서 처리
) {}
