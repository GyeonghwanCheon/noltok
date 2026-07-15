package com.example.noltok.user.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        // PATCH 특성상 안 보낸 필드는 null → updateProfile()에서 기존 값 유지
        @Size(min = 2, max = 10, message = "닉네임은 2~10자로 입력해주세요.")
        String nickname,

        // URL 형식 검증은 클라이언트 책임, 서버는 저장만
        String profileImageUrl

) {}
