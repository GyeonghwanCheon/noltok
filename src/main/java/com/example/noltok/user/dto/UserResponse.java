package com.example.noltok.user.dto;

import com.example.noltok.user.User;
import java.time.LocalDate;

public record UserResponse(

        Long userId,
        String email,
        String nickname,
        String profileImageUrl,
        LocalDate createdAt

) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getCreatedAt().toLocalDate()
        );
    }
}
