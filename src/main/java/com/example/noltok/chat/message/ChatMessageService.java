package com.example.noltok.chat.message;

import com.example.noltok.chat.ChatRoom;
import com.example.noltok.chat.ChatRoomMemberRepository;
import com.example.noltok.chat.ChatRoomRepository;
import com.example.noltok.chat.message.dto.request.SendMessageRequest;
import com.example.noltok.chat.message.dto.response.ChatMessageListResponse;
import com.example.noltok.chat.message.dto.response.ChatMessageResponse;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
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

    @Transactional(readOnly = true)
    public ChatMessageListResponse getMessages(Long userId, Long roomId, Long cursor, int size) {
        // 1. 채팅방 존재 확인
        chatRoomRepository.findById(roomId)
                .filter(ChatRoom::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        // 2. 요청자가 활성 멤버인지 확인
        chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHATROOM_MEMBER));

        // 3. 커서 기반 조회 (id DESC), Slice가 다음 페이지 존재 여부를 자동 판단
        Pageable pageable = PageRequest.of(0, size);
        Slice<ChatMessage> slice = cursor == null
                ? chatMessageRepository.findByRoomIdOrderByIdDesc(roomId, pageable)
                : chatMessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(roomId, cursor, pageable);

        // 4. 발신자 정보 일괄 조회 (N+1 방지)
        List<Long> senderIds = slice.getContent().stream().map(ChatMessage::getSenderId).distinct().toList();
        Map<Long, User> userMap = userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        // 5. 오래된 순으로 뒤집어서 응답 구성
        List<ChatMessage> ascending = new ArrayList<>(slice.getContent());
        Collections.reverse(ascending);
        List<ChatMessageResponse> messages = ascending.stream()
                .map(m -> ChatMessageResponse.of(m, userMap.get(m.getSenderId()).getNickname()))
                .toList();

        return ChatMessageListResponse.of(messages, slice.hasNext());
    }
}
