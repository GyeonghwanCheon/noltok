package com.example.noltok.user.dto;

import com.example.noltok.user.User;

public record UserSummaryResponse(

        Long userId,
        String nickname,
        String profileImageUrl
        // email, createdAt 제외 이유:
        // → 타인의 이메일은 개인정보로 노출 금지
        // → 가입일은 채팅 상대 찾기에 불필요한 정보

) {
    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl()
        );
    }
}
