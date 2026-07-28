package com.example.noltok.user.dto;

import com.example.noltok.user.User;

// 여러 도메인(Chat/Friend/Block)이 공통으로 쓰는 유저 프로필 조각 — email/password 등
// 민감정보는 제외하고 화면에 표시되는 최소 정보만 담음 (Redis 캐시 값으로도 사용)
public record UserProfileDto(
        Long userId,
        String nickname,
        String profileImageUrl
) {
    public static UserProfileDto from(User user) {
        return new UserProfileDto(user.getId(), user.getNickname(), user.getProfileImageUrl());
    }
}
