package com.example.noltok.user.dto;

import com.example.noltok.user.User;

import java.time.LocalDate;

public record SignUpResponse(

        Long userId,
        String email,
        String nickname,
        LocalDate createdAt

) {
    // Entity → DTO 변환 책임을 DTO 스스로 갖도록 설계 (정적 팩토리 메서드)
    public static SignUpResponse from(User user) {
        return new SignUpResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getCreatedAt().toLocalDate()   // LocalDateTime → LocalDate 변환
        );
    }
}
