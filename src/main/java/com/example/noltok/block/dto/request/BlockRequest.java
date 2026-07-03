package com.example.noltok.block.dto.request;

import jakarta.validation.constraints.NotBlank;

public record BlockRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        String nickname
) {}
