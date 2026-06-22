package com.example.noltok.user.dto;

public record DeleteAccountResponse(
        Long userId
        // message는 Controller에서 ApiResponse로 처리
        // DTO에는 userId만 포함
) {}
