package com.example.noltok.chat.message.dto.request;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(

        @NotBlank(message = "메시지 내용은 필수입니다.")
        String content

) {}
