package com.example.noltok.user.dto.response;

import com.example.noltok.user.User;

import java.time.LocalDate;

public record SignUpResponse(

        Long userId,
        String email,
        String nickname,
        LocalDate createdAt

) {
    public static SignUpResponse from(User user) {
        return new SignUpResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getCreatedAt().toLocalDate()
        );
    }
}
