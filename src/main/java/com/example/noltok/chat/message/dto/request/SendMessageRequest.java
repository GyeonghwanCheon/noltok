package com.example.noltok.chat.message.dto.request;

import com.example.noltok.chat.message.ChatMessageType;
import jakarta.validation.constraints.NotNull;

public record SendMessageRequest(

        @NotNull(message = "메시지 타입은 필수입니다.")
        ChatMessageType type,

        // 타입별 필수 여부가 달라 Bean Validation 대신 Service에서 검증
        String content,

        // IMAGE/FILE 타입일 때 필수, TEXT는 사용 안 함
        String fileUrl

) {}
