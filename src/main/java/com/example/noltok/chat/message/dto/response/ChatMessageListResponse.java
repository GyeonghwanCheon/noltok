package com.example.noltok.chat.message.dto.response;

import java.util.List;

public record ChatMessageListResponse(
        List<ChatMessageResponse> messages,
        boolean hasNext,
        Long nextCursor
) {
    // nextCursor: 응답이 오래된 순으로 정렬돼 있으므로 첫 번째(가장 오래된) 메시지의 id
    public static ChatMessageListResponse of(List<ChatMessageResponse> messages, boolean hasNext) {
        Long nextCursor = messages.isEmpty() ? null : messages.get(0).messageId();
        return new ChatMessageListResponse(messages, hasNext, nextCursor);
    }
}
