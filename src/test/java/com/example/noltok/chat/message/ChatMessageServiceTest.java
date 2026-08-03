package com.example.noltok.chat.message;

import com.example.noltok.block.BlockCacheService;
import com.example.noltok.chat.ChatRoom;
import com.example.noltok.chat.ChatRoomMemberRepository;
import com.example.noltok.chat.ChatRoomRepository;
import com.example.noltok.chat.ChatRoomMember;
import com.example.noltok.chat.ChatRoomRole;
import com.example.noltok.chat.UnreadCountCacheService;
import com.example.noltok.chat.message.dto.request.SendMessageRequest;
import com.example.noltok.chat.message.dto.response.ChatMessageDeleteResponse;
import com.example.noltok.chat.message.dto.response.ChatMessageListResponse;
import com.example.noltok.chat.message.kafka.ChatMessageProducer;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.User;
import com.example.noltok.user.UserProfileCacheService;
import com.example.noltok.user.dto.UserProfileDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private UserProfileCacheService userProfileCacheService;
    @Mock
    private ChatMessageProducer chatMessageProducer;
    @Mock
    private BlockCacheService blockCacheService;
    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;
    @Mock
    private UnreadCountCacheService unreadCountCacheService;

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
                chatRoomMemberRepository, userProfileCacheService, chatMessageProducer, blockCacheService,
                simpMessagingTemplate, unreadCountCacheService);
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
        given(blockCacheService.getBlockedByMe(userId)).willReturn(List.of());
        given(chatMessageRepository.findByRoomIdAndSenderIdNotInOrderByIdDesc(eq(roomId), eq(List.of(-1L)), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(message), PageRequest.of(0, 20), false));
        given(userProfileCacheService.getProfiles(List.of(userId)))
                .willReturn(Map.of(userId, UserProfileDto.from(sender)));

        // when
        ChatMessageListResponse response = chatMessageService.getMessages(userId, roomId, null, 20);

        // then
        assertThat(response.messages()).hasSize(1);
        verify(chatMessageRepository).findByRoomIdAndSenderIdNotInOrderByIdDesc(eq(roomId), eq(List.of(-1L)), any(Pageable.class));
        verify(chatMessageRepository, never()).findByRoomIdAndSenderIdNotInAndIdLessThanOrderByIdDesc(any(), any(), anyLong(), any());
    }

    @Test
    void getMessages_커서가_있으면_그보다_이전_메시지를_조회한다() {
        // given
        User sender = testUser(userId, "발신자");
        ChatMessage message = testMessage(3L, userId, "이전 메시지");
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(activeRoom(roomId)));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId))
                .willReturn(Optional.of(ChatRoomMember.create(activeRoom(roomId), userId, ChatRoomRole.MEMBER)));
        given(blockCacheService.getBlockedByMe(userId)).willReturn(List.of());
        given(chatMessageRepository.findByRoomIdAndSenderIdNotInAndIdLessThanOrderByIdDesc(
                eq(roomId), eq(List.of(-1L)), eq(5L), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(message), PageRequest.of(0, 20), true));
        given(userProfileCacheService.getProfiles(List.of(userId)))
                .willReturn(Map.of(userId, UserProfileDto.from(sender)));

        // when
        ChatMessageListResponse response = chatMessageService.getMessages(userId, roomId, 5L, 20);

        // then
        assertThat(response.hasNext()).isTrue();
        verify(chatMessageRepository, never()).findByRoomIdAndSenderIdNotInOrderByIdDesc(any(), any(), any());
    }

    @Test
    void getMessages_내가_차단한_사람의_메시지는_제외하고_조회한다() {
        // given: 차단한 유저(99L)의 id를 NOT IN 조건으로 그대로 넘기는지 확인
        User sender = testUser(userId, "발신자");
        ChatMessage message = testMessage(5L, userId, "최신 메시지");
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(activeRoom(roomId)));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId))
                .willReturn(Optional.of(ChatRoomMember.create(activeRoom(roomId), userId, ChatRoomRole.MEMBER)));
        given(blockCacheService.getBlockedByMe(userId)).willReturn(List.of(99L));
        given(chatMessageRepository.findByRoomIdAndSenderIdNotInOrderByIdDesc(eq(roomId), eq(List.of(99L)), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(message), PageRequest.of(0, 20), false));
        given(userProfileCacheService.getProfiles(List.of(userId)))
                .willReturn(Map.of(userId, UserProfileDto.from(sender)));

        // when
        chatMessageService.getMessages(userId, roomId, null, 20);

        // then: 차단 목록(99L)이 sentinel(-1L) 대신 그대로 NOT IN 조건에 쓰였는지
        verify(chatMessageRepository).findByRoomIdAndSenderIdNotInOrderByIdDesc(eq(roomId), eq(List.of(99L)), any(Pageable.class));
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
        given(blockCacheService.getBlockedByMe(userId)).willReturn(List.of());
        given(chatMessageRepository.findByRoomIdAndSenderIdNotInOrderByIdDesc(eq(roomId), eq(List.of(-1L)), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(newest, middle, oldest), PageRequest.of(0, 20), false));
        given(userProfileCacheService.getProfiles(List.of(userId)))
                .willReturn(Map.of(userId, UserProfileDto.from(sender)));

        // when
        ChatMessageListResponse response = chatMessageService.getMessages(userId, roomId, null, 20);

        // then: 응답은 시간순(오래된 → 최신)이어야 함
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

    // ── deleteMessage() ────────────────────────────────────────

    @Test
    void deleteMessage_정상_삭제시_Hard_Delete하고_활성_멤버_전원에게_실시간_전파하고_안읽음_캐시를_무효화한다() {
        // given
        Long messageId = 5L;
        ChatMessage message = testMessage(messageId, userId, "지울 메시지");
        ChatRoomMember me = ChatRoomMember.create(activeRoom(roomId), userId, ChatRoomRole.MEMBER);
        ChatRoomMember other = ChatRoomMember.create(activeRoom(roomId), 2L, ChatRoomRole.MEMBER);
        given(chatMessageRepository.findById(messageId)).willReturn(Optional.of(message));
        given(chatRoomMemberRepository.findByChatRoomIdAndIsActiveTrue(roomId)).willReturn(List.of(me, other));

        // when
        ChatMessageDeleteResponse response = chatMessageService.deleteMessage(userId, roomId, messageId);

        // then
        assertThat(response.messageId()).isEqualTo(messageId);
        verify(chatMessageRepository).delete(message);
        verify(simpMessagingTemplate).convertAndSendToUser(eq(String.valueOf(userId)),
                eq("/queue/rooms/" + roomId + "/deleted"), eq(response));
        verify(simpMessagingTemplate).convertAndSendToUser(eq(String.valueOf(2L)),
                eq("/queue/rooms/" + roomId + "/deleted"), eq(response));
        verify(unreadCountCacheService, times(2)).invalidate(any());
    }

    @Test
    void deleteMessage_메시지가_없으면_예외() {
        // given
        given(chatMessageRepository.findById(5L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatMessageService.deleteMessage(userId, roomId, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHATMESSAGE_NOT_FOUND);
        verify(chatMessageRepository, never()).delete(any());
    }

    @Test
    void deleteMessage_다른_방의_메시지면_예외() {
        // given: 메시지는 roomId(10L)가 아니라 다른 방(99L) 소속
        ChatMessage message = ChatMessage.createText(99L, userId, "다른 방 메시지");
        ReflectionTestUtils.setField(message, "id", 5L);
        given(chatMessageRepository.findById(5L)).willReturn(Optional.of(message));

        // when & then
        assertThatThrownBy(() -> chatMessageService.deleteMessage(userId, roomId, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHATMESSAGE_NOT_FOUND);
        verify(chatMessageRepository, never()).delete(any());
    }

    @Test
    void deleteMessage_본인이_보낸_메시지가_아니면_예외() {
        // given: 발신자는 99L, 요청자는 userId(1L)
        ChatMessage message = testMessage(5L, 99L, "남의 메시지");
        given(chatMessageRepository.findById(5L)).willReturn(Optional.of(message));

        // when & then
        assertThatThrownBy(() -> chatMessageService.deleteMessage(userId, roomId, 5L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_MESSAGE_SENDER);
        verify(chatMessageRepository, never()).delete(any());
    }

    // ── searchMessages() ──────────────────────────────────────

    @Test
    void searchMessages_정상_검색시_결과를_재정렬없이_최신순_그대로_반환한다() {
        // given: DB는 최신순(id DESC)으로 반환 — getMessages()와 달리 뒤집지 않아야 함
        User sender = testUser(userId, "발신자");
        ChatMessage newest = testMessage(7L, userId, "안녕 검색어 포함");
        ChatMessage oldest = testMessage(5L, userId, "검색어 포함 안녕");
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(activeRoom(roomId)));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId))
                .willReturn(Optional.of(ChatRoomMember.create(activeRoom(roomId), userId, ChatRoomRole.MEMBER)));
        given(blockCacheService.getBlockedByMe(userId)).willReturn(List.of());
        given(chatMessageRepository.searchByRoomIdAndKeyword(eq(roomId), eq(List.of(-1L)), eq("검색어"), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(newest, oldest), PageRequest.of(0, 20), false));
        given(userProfileCacheService.getProfiles(List.of(userId)))
                .willReturn(Map.of(userId, UserProfileDto.from(sender)));

        // when
        ChatMessageListResponse response = chatMessageService.searchMessages(userId, roomId, "검색어", null, 20);

        // then: DB가 반환한 순서(7, 5) 그대로 유지
        assertThat(response.messages()).extracting("messageId").containsExactly(7L, 5L);
        verify(chatMessageRepository, never()).searchByRoomIdAndKeywordAndIdLessThan(any(), any(), anyLong(), any(), any());
    }

    @Test
    void searchMessages_커서가_있으면_그보다_이전_검색결과를_조회한다() {
        // given
        User sender = testUser(userId, "발신자");
        ChatMessage message = testMessage(3L, userId, "검색어 포함");
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(activeRoom(roomId)));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId))
                .willReturn(Optional.of(ChatRoomMember.create(activeRoom(roomId), userId, ChatRoomRole.MEMBER)));
        given(blockCacheService.getBlockedByMe(userId)).willReturn(List.of());
        given(chatMessageRepository.searchByRoomIdAndKeywordAndIdLessThan(
                eq(roomId), eq(List.of(-1L)), eq(5L), eq("검색어"), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(message), PageRequest.of(0, 20), true));
        given(userProfileCacheService.getProfiles(List.of(userId)))
                .willReturn(Map.of(userId, UserProfileDto.from(sender)));

        // when
        ChatMessageListResponse response = chatMessageService.searchMessages(userId, roomId, "검색어", 5L, 20);

        // then
        assertThat(response.hasNext()).isTrue();
        verify(chatMessageRepository, never()).searchByRoomIdAndKeyword(any(), any(), any(), any());
    }

    @Test
    void searchMessages_키워드가_비어있으면_예외() {
        // when & then
        assertThatThrownBy(() -> chatMessageService.searchMessages(userId, roomId, " ", null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(chatMessageRepository, never()).searchByRoomIdAndKeyword(any(), any(), any(), any());
    }

    @Test
    void searchMessages_채팅방이_없으면_예외() {
        // given
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatMessageService.searchMessages(userId, roomId, "검색어", null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHATROOM_NOT_FOUND);
    }

    @Test
    void searchMessages_활성_멤버가_아니면_예외() {
        // given
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(activeRoom(roomId)));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId))
                .willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatMessageService.searchMessages(userId, roomId, "검색어", null, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_CHATROOM_MEMBER);
    }

    @Test
    void searchMessages_차단한_사람의_메시지는_제외하고_검색한다() {
        // given: 차단한 유저(99L)의 id를 NOT IN 조건으로 그대로 넘기는지 확인
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(activeRoom(roomId)));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId))
                .willReturn(Optional.of(ChatRoomMember.create(activeRoom(roomId), userId, ChatRoomRole.MEMBER)));
        given(blockCacheService.getBlockedByMe(userId)).willReturn(List.of(99L));
        given(chatMessageRepository.searchByRoomIdAndKeyword(eq(roomId), eq(List.of(99L)), eq("검색어"), any(Pageable.class)))
                .willReturn(new SliceImpl<>(List.of(), PageRequest.of(0, 20), false));

        // when
        chatMessageService.searchMessages(userId, roomId, "검색어", null, 20);

        // then
        verify(chatMessageRepository).searchByRoomIdAndKeyword(eq(roomId), eq(List.of(99L)), eq("검색어"), any(Pageable.class));
    }
}
