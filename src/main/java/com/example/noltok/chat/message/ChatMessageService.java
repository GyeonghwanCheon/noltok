package com.example.noltok.chat.message;

import com.example.noltok.chat.ChatRoom;
import com.example.noltok.chat.ChatRoomMemberRepository;
import com.example.noltok.chat.ChatRoomRepository;
import com.example.noltok.chat.message.dto.request.SendMessageRequest;
import com.example.noltok.chat.message.dto.response.ChatMessageListResponse;
import com.example.noltok.chat.message.dto.response.ChatMessageResponse;
import com.example.noltok.chat.message.kafka.ChatMessageProducer;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
    private final ChatMessageProducer chatMessageProducer;

    @Transactional(readOnly = true)
    public void sendMessage(Long roomId, Long senderId, SendMessageRequest request) {
        // 1. 발신자가 활성 멤버인지 확인 (동기 검증, Kafka를 거치지 않고 즉시 거부)
        chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, senderId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHATROOM_MEMBER));

        // 2. 타입별 필수값 검증 (CreateRoomRequest.validateRoomFields()와 동일 패턴)
        if (request.type() == ChatMessageType.TEXT && (request.content() == null || request.content().isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        if ((request.type() == ChatMessageType.IMAGE || request.type() == ChatMessageType.FILE)
                && (request.fileUrl() == null || request.fileUrl().isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 3. 검증 통과 시 저장/브로드캐스트는 Kafka로 위임 (ChatMessageConsumer가 비동기 처리)
        chatMessageProducer.publish(roomId, senderId, request.type(), request.content(), request.fileUrl());
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
