package com.example.noltok.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
        // 로그인 시 비밀번호 패턴 검증은 하지 않음
        // 이유: 어차피 DB의 해시값과 비교하므로 형식 검증은 불필요
        //       "틀린 비밀번호"와 "형식 오류"를 구분할 이유가 없음
) {}
