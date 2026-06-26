package com.example.noltok.user.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(

        // nullable = true 이유:
        // → PATCH는 보낸 필드만 수정
        // → nickname을 안 보내면 null → updateProfile()에서 기존 값 유지
        @Size(min = 2, max = 10, message = "닉네임은 2~10자로 입력해주세요.")
        String nickname,

        // profileImageUrl은 형식 검증 없이 String 그대로 받음
        // 이유: URL 형식 검증은 클라이언트에서 처리, 서버는 저장만 담당
        String profileImageUrl

) {}
