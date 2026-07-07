package com.example.noltok.chat.message;

import com.example.noltok.chat.message.dto.request.SendMessageRequest;
import com.example.noltok.chat.message.dto.response.ChatMessageListResponse;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @MessageMapping("/rooms/{roomId}/messages")
    public void sendMessage(@DestinationVariable Long roomId,
                             @Payload SendMessageRequest request,
                             Principal principal) {
        Long senderId = Long.parseLong(principal.getName());

        // 실패 시 방 전체가 아니라 보낸 사람에게만 개인적으로 에러 전달
        try {
            chatMessageService.sendMessage(roomId, senderId, request);
        } catch (BusinessException e) {
            simpMessagingTemplate.convertAndSendToUser(
                    principal.getName(), "/queue/errors", e.getErrorCode().getMessage());
        }
    }

    @GetMapping("/api/v1/chat/rooms/{roomId}/messages")
    @ResponseBody
    public ResponseEntity<ApiResponse<ChatMessageListResponse>> getMessages(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ChatMessageListResponse response = chatMessageService.getMessages(userId, roomId, cursor, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
