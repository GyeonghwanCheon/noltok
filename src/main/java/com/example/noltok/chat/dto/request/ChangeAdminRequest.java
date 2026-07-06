package com.example.noltok.chat.dto.request;

import jakarta.validation.constraints.NotNull;

public record ChangeAdminRequest(

        @NotNull(message = "신규 관리자 대상 유저는 필수입니다.")
        Long newAdminUserId

) {}
