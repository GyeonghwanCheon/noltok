package com.example.noltok.user.dto.response;

import com.example.noltok.user.User;

public record UserSummaryResponse(

        Long userId,
        String nickname,
        String profileImageUrl
        // email/createdAt 제외 — 타인 이메일 노출 방지, 가입일은 불필요한 정보

) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }
}
