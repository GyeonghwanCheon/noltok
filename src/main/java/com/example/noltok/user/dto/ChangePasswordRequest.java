package com.example.noltok.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(

        @NotBlank(message = "현재 비밀번호는 필수입니다.")
        String currentPassword,

        @NotBlank(message = "새 비밀번호는 필수입니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "비밀번호는 8자 이상, 영문과 숫자를 포함해야 합니다."
        )
        String newPassword,

        @NotBlank(message = "비밀번호 확인은 필수입니다.")
        String confirmPassword
        // confirmPassword에 @Pattern을 붙이지 않는 이유:
        // → newPassword와 일치 여부만 확인하면 됨
        // → 형식 검증은 newPassword에서 이미 처리
        // → confirmPassword에도 패턴 검증을 하면
        //   "비밀번호 형식이 틀렸습니다"와 "비밀번호가 일치하지 않습니다"
        //   두 에러가 동시에 날 수 있어 사용자 혼란 유발

) {}
