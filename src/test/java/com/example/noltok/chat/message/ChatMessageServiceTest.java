package com.example.noltok.chat.message;

import com.example.noltok.chat.ChatRoom;
import com.example.noltok.chat.ChatRoomMemberRepository;
import com.example.noltok.chat.ChatRoomRepository;
import com.example.noltok.chat.ChatRoomMember;
import com.example.noltok.chat.ChatRoomRole;
import com.example.noltok.chat.message.dto.request.SendMessageRequest;
import com.example.noltok.chat.message.dto.response.ChatMessageListResponse;
import com.example.noltok.chat.message.kafka.ChatMessageProducer;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// DB/Kafka 없이 순수 JVM에서 도는 단위 테스트 — Repository/Producer는 전부 Mock
@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatMessageProducer chatMessageProducer;

    private ChatMessageService chatMessageService;

    private final Long userId = 1L;
    private final Long roomId = 10L;

    private ChatRoom activeRoom(Long id) {
        ChatRoom room = ChatRoom.create("테스트방", com.example.noltok.chat.ChatRoomType.OPEN, userId, null);
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }

    private ChatMessage testMessage(Long id, Long senderId, String content) {
        ChatMessage message = ChatMessage.createText(roomId, senderId, content);
        ReflectionTestUtils.setField(message, "id", id);
        return message;
    }

    private User testUser(Long id, String nickname) {
        User user = User.create(nickname + "@test.com", "encoded-pw", nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @BeforeEach
    void setUp() {
        chatMessageService = new ChatMessageService(chatMessageRepository, chatRoomRepository,
                chatRoomMemberRepository, userRepository, chatMessageProducer);
    }

    // ── sendMessage() ──────────────────────────────────────────

    @Test
    void sendMessage_정상_TEXT_전송시_Kafka로_발행한다() {
        // given
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId))
                .willReturn(Optional.of(ChatRoomMember.create(activeRoom(roomId), userId, ChatRoomRole.MEMBER)));

        // when
        chatMessageService.sendMessage(roomId, userId, new SendMessageRequest(ChatMessageType.TEXT, "안녕", null));

        // then
        verify(chatMessageProducer).publish(roomId, userId, ChatMessageType.TEXT, "안녕", null);
    }

    @Test
    void sendMessage_활성_멤버가_아니면_예외이고_발행되지_않는다() {
        // given
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatMessageService.sendMessage(roomId, userId,
                new SendMessageRequest(ChatMessageType.TEXT, "안녕", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_CHATROOM_MEMBER);
        verify(chatMessageProducer, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void sendMessage_TEXT인데_content가_없으면_예외이고_발행되지_않는다() {
        // given
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId))
                .willReturn(Optional.of(ChatRoomMember.create(activeRoom(roomId), userId, ChatRoomRole.MEMBER)));

        // when & then
        assertThatThrownBy(() -> chatMessageService.sendMessage(roomId, userId,
                new SendMessageRequest(ChatMessageType.TEXT, "", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(chatMessageProducer, never()).publish(any(), any(), any(), any(), any());
    }

    @Test
    void sendMessage_IMAGE인데_fileUrl이_없으면_예외이고_발행되지_않는다() {
        // given
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId))
                .willReturn(Optional.of(ChatRoomMember.create(activeRoom(roomId), userId, ChatRoomRole.MEMBER)));

        // when & then
        assertThatThrownBy(() -> chatMessageService.sendMessage(roomId, userId,
                new SendMessageRequest(ChatMessageType.IMAGE, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(chatMessageProducer, never()).publish(any(), any(), any(), any(), any());
    }

    // ── getMessages() ──────────────────────────────────────────

    @Test
    void getMessages_cursor가_없으면_최신_페이지를_조회한다() {
        // given
        User sender = testUser(userId, "발신자");
        ChatMessage message = testMessage(5L, userId, "최신 메시지");
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(activeRoom(roomId)));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId))
                .willReturn(Optional.of(ChatRoomMember.create(activeRoom(roomId), userId, ChatRoomRole.MEMBER)));
        given(chatMessageRepository.findByRoomIdOrderByIdDesc(anyLong(), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(message), PageRequest.of(0, 20), false));
        given(userRepository.findAllById(List.of(userId))).willReturn(List.of(sender));

        // when
        ChatMessageListResponse response = chatMessageService.getMessages(userId, roomId, null, 20);

        // then
        assertThat(response.messages()).hasSize(1);
        verify(chatMessageRepository).findByRoomIdOrderByIdDesc(anyLong(), any(Pageable.class));
        verify(chatMessageRepository, never()).findByRoomIdAndIdLessThanOrderByIdDesc(anyLong(), anyLong(), any());
    }

    @Test
    void getMessages_cursor가_있으면_그보다_이전_메시지를_조회한다() {
        // given
        User sender = testUser(userId, "발신자");
        ChatMessage message = testMessage(3L, userId, "이전 메시지");
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(activeRoom(roomId)));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId))
                .willReturn(Optional.of(ChatRoomMember.create(activeRoom(roomId), userId, ChatRoomRole.MEMBER)));
        given(chatMessageRepository.findByRoomIdAndIdLessThanOrderByIdDesc(eq(roomId), eq(5L), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(message), PageRequest.of(0, 20), true));
        given(userRepository.findAllById(List.of(userId))).willReturn(List.of(sender));

        // when
        ChatMessageListResponse response = chatMessageService.getMessages(userId, roomId, 5L, 20);

        // then
        assertThat(response.hasNext()).isTrue();
        verify(chatMessageRepository, never()).findByRoomIdOrderByIdDesc(anyLong(), any());
    }

    @Test
    void getMessages_응답은_오래된_순으로_뒤집혀서_반환된다() {
        // given: DB는 항상 최신순(id DESC)으로 반환 — id 7(최신), 6, 5(가장 오래됨) 순
        User sender = testUser(userId, "발신자");
        ChatMessage newest = testMessage(7L, userId, "세 번째로 보낸 메시지");
        ChatMessage middle = testMessage(6L, userId, "두 번째로 보낸 메시지");
        ChatMessage oldest = testMessage(5L, userId, "첫 번째로 보낸 메시지");
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(activeRoom(roomId)));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId))
                .willReturn(Optional.of(ChatRoomMember.create(activeRoom(roomId), userId, ChatRoomRole.MEMBER)));
        given(chatMessageRepository.findByRoomIdOrderByIdDesc(anyLong(), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(newest, middle, oldest), PageRequest.of(0, 20), false));
        given(userRepository.findAllById(List.of(userId))).willReturn(List.of(sender));

        // when
        ChatMessageListResponse response = chatMessageService.getMessages(userId, roomId, null, 20);

        // then: 응답은 시간순(오래된 → 최신)이어야 함 (decision-log.md 2026-07-07)
        assertThat(response.messages()).extracting("content")
                .containsExactly("첫 번째로 보낸 메시지", "두 번째로 보낸 메시지", "세 번째로 보낸 메시지");
    }

    @Test
    void getMessages_채팅방이_없으면_예외() {
        // given
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatMessageService.getMessages(userId, roomId, null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHATROOM_NOT_FOUND);
    }
}
