package com.example.noltok.chat.message;

import com.example.noltok.chat.message.dto.request.SendMessageRequest;
import com.example.noltok.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

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
}
