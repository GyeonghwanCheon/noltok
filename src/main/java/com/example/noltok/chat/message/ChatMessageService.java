package com.example.noltok.chat.message;

import com.example.noltok.chat.ChatRoomMemberRepository;
import com.example.noltok.chat.message.dto.request.SendMessageRequest;
import com.example.noltok.chat.message.dto.response.ChatMessageResponse;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    @Transactional
    public void sendMessage(Long roomId, Long senderId, SendMessageRequest request) {
        // 1. 발신자가 활성 멤버인지 확인
        chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHATROOM_MEMBER));

        // 2. 메시지 저장
        ChatMessage message = ChatMessage.createText(roomId, senderId, request.content());
        chatMessageRepository.save(message);

        // 3. 발신자 닉네임 조회
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 4. 방 구독자 전원에게 브로드캐스트 (Kafka 없는 동기 버전)
        ChatMessageResponse response = ChatMessageResponse.of(message, sender.getNickname());
        simpMessagingTemplate.convertAndSend("/topic/rooms/" + roomId, response);
    }
}
