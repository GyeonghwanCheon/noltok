package com.example.noltok.chat;

import com.example.noltok.block.BlockRepository;
import com.example.noltok.chat.dto.request.ChangeAdminRequest;
import com.example.noltok.chat.dto.request.CreateRoomRequest;
import com.example.noltok.chat.dto.request.InviteMembersRequest;
import com.example.noltok.chat.dto.response.ChatRoomAdminResponse;
import com.example.noltok.chat.dto.response.ChatRoomDeleteResponse;
import com.example.noltok.chat.dto.response.ChatRoomInviteResponse;
import com.example.noltok.chat.dto.response.ChatRoomJoinResponse;
import com.example.noltok.chat.dto.response.ChatRoomKickResponse;
import com.example.noltok.chat.dto.response.ChatRoomLeaveResponse;
import com.example.noltok.chat.dto.response.ChatRoomResponse;
import com.example.noltok.chat.message.ChatMessageRepository;
import com.example.noltok.friend.Friend;
import com.example.noltok.friend.FriendRepository;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.notification.kafka.NotificationProducer;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

// DB/Kafka/Redis 없이 순수 JVM에서 도는 단위 테스트 — Repository/Producer/캐시는 전부 Mock
// getRoomDetail()/searchRooms()는 ChatRoomServiceN1RegressionTest(Testcontainers)에서
// 쿼리 수 관점으로 이미 검증하고 있어 이 클래스에서는 다루지 않음
@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;
    @Mock
    private ChatMessageRepository chatMessageRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FriendRepository friendRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private NotificationProducer notificationProducer;
    @Mock
    private UnreadCountCacheService unreadCountCacheService;

    private ChatRoomService chatRoomService;

    private final Long adminId = 1L;
    private final Long targetId = 2L;
    private final Long roomId = 10L;

    private User testUser(Long id, String nickname) {
        User user = User.create(nickname + "@test.com", "encoded-pw", nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // ChatRoom.create()는 실제 JPA 영속화 없이 만들어져서 createdAt이 비어있는데,
    // ChatRoomResponse.of()가 이걸 필요로 해서 save() 시점에 채워주는 스텁
    private void stubSaveFillsCreatedAt() {
        lenient().when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> {
            ChatRoom room = invocation.getArgument(0);
            ReflectionTestUtils.setField(room, "createdAt", LocalDateTime.now());
            return room;
        });
    }

    private ChatRoom room(Long id, ChatRoomType type, boolean active) {
        ChatRoom room = ChatRoom.create(type == ChatRoomType.DIRECT ? null : "테스트방", type, adminId, null);
        if (!active) room.deactivate();
        ReflectionTestUtils.setField(room, "id", id);
        ReflectionTestUtils.setField(room, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(room, "updatedAt", LocalDateTime.now());
        return room;
    }

    private ChatRoomMember member(ChatRoom chatRoom, Long userId, ChatRoomRole role) {
        return ChatRoomMember.create(chatRoom, userId, role);
    }

    @BeforeEach
    void setUp() {
        chatRoomService = new ChatRoomService(chatRoomRepository, chatRoomMemberRepository, chatMessageRepository,
                userRepository, friendRepository, blockRepository, passwordEncoder, notificationProducer,
                unreadCountCacheService);
    }

    // ── createRoom() ───────────────────────────────────────────

    @Test
    void createRoom_GROUP_정상_생성시_초대유저_전원을_MEMBER로_저장한다() {
        // given
        User invitee = testUser(targetId, "친구");
        Friend accepted = Friend.create(adminId, targetId);
        accepted.accept();
        stubSaveFillsCreatedAt();
        given(userRepository.findByNickname("친구")).willReturn(Optional.of(invitee));
        given(friendRepository.findRelationBetween(adminId, targetId)).willReturn(Optional.of(accepted));
        given(blockRepository.existsActiveBlockBetween(adminId, targetId)).willReturn(false);

        // when
        ChatRoomResponse response = chatRoomService.createRoom(adminId,
                new CreateRoomRequest("개발팀방", ChatRoomType.GROUP, null, List.of("친구")));

        // then
        assertThat(response.memberCount()).isEqualTo(2); // 생성자(ADMIN) + 초대 1명
        assertThat(response.myRole()).isEqualTo("ADMIN");
    }

    @Test
    void createRoom_GROUP인데_roomname이_없으면_예외() {
        // when & then
        assertThatThrownBy(() -> chatRoomService.createRoom(adminId,
                new CreateRoomRequest(null, ChatRoomType.GROUP, null, List.of("친구"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void createRoom_초대_대상이_친구가_아니면_예외() {
        // given
        User invitee = testUser(targetId, "남남");
        given(userRepository.findByNickname("남남")).willReturn(Optional.of(invitee));
        given(friendRepository.findRelationBetween(adminId, targetId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatRoomService.createRoom(adminId,
                new CreateRoomRequest("방이름", ChatRoomType.GROUP, null, List.of("남남"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHATROOM_INVITE_NOT_FRIEND);
    }

    @Test
    void createRoom_OPEN_PRIVATE는_비밀번호를_암호화해서_저장한다() {
        // given
        stubSaveFillsCreatedAt();
        given(passwordEncoder.encode("1234")).willReturn("encoded-1234");

        // when
        chatRoomService.createRoom(adminId,
                new CreateRoomRequest("오픈방", ChatRoomType.OPEN_PRIVATE, "1234", null));

        // then
        org.mockito.Mockito.verify(passwordEncoder).encode("1234");
    }

    // ── joinRoom() ─────────────────────────────────────────────

    @Test
    void joinRoom_OPEN_방에_신규_입장한다() {
        // given
        ChatRoom openRoom = room(roomId, ChatRoomType.OPEN, true);
        User user = testUser(targetId, "입장유저");
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(openRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, targetId)).willReturn(Optional.empty());
        given(userRepository.findById(targetId)).willReturn(Optional.of(user));

        // when
        ChatRoomJoinResponse response = chatRoomService.joinRoom(targetId, roomId, null);

        // then
        assertThat(response.message()).contains("입장유저");
        org.mockito.Mockito.verify(chatRoomMemberRepository).save(any(ChatRoomMember.class));
    }

    @Test
    void joinRoom_DIRECT_GROUP은_자유입장_불가() {
        // given
        ChatRoom groupRoom = room(roomId, ChatRoomType.GROUP, true);
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(groupRoom));

        // when & then
        assertThatThrownBy(() -> chatRoomService.joinRoom(targetId, roomId, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_SELF_JOIN_ROOM);
    }

    @Test
    void joinRoom_OPEN_PRIVATE_비밀번호가_틀리면_예외() {
        // given
        ChatRoom privateRoom = ChatRoom.create("비번방", ChatRoomType.OPEN_PRIVATE, adminId, "encoded-correct");
        ReflectionTestUtils.setField(privateRoom, "id", roomId);
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(privateRoom));
        given(passwordEncoder.matches("wrong", "encoded-correct")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> chatRoomService.joinRoom(targetId, roomId, "wrong"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CHATROOM_PASSWORD);
    }

    // ── inviteMembers() ────────────────────────────────────────

    @Test
    void inviteMembers_정상_초대시_알림을_발행한다() {
        // given
        ChatRoom groupRoom = room(roomId, ChatRoomType.GROUP, true);
        User inviter = testUser(adminId, "초대자");
        User invitee = testUser(targetId, "피초대자");
        Friend accepted = Friend.create(adminId, targetId);
        accepted.accept();
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(groupRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, adminId))
                .willReturn(Optional.of(member(groupRoom, adminId, ChatRoomRole.ADMIN)));
        given(userRepository.findByNickname("피초대자")).willReturn(Optional.of(invitee));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, targetId))
                .willReturn(Optional.empty());
        given(friendRepository.findRelationBetween(adminId, targetId)).willReturn(Optional.of(accepted));
        given(blockRepository.existsActiveBlockBetween(adminId, targetId)).willReturn(false);
        given(chatRoomMemberRepository.findByChatRoomIdAndUserId(roomId, targetId)).willReturn(Optional.empty());
        given(userRepository.findById(adminId)).willReturn(Optional.of(inviter));

        // when
        ChatRoomInviteResponse response = chatRoomService.inviteMembers(adminId, roomId,
                new InviteMembersRequest(List.of("피초대자")));

        // then
        assertThat(response.invitedMembers()).hasSize(1);
        org.mockito.Mockito.verify(notificationProducer).publish(eq(targetId),
                any(), org.mockito.ArgumentMatchers.contains("초대"));
    }

    @Test
    void inviteMembers_DIRECT_방은_초대_불가() {
        // given
        ChatRoom directRoom = room(roomId, ChatRoomType.DIRECT, true);
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(directRoom));

        // when & then
        assertThatThrownBy(() -> chatRoomService.inviteMembers(adminId, roomId,
                new InviteMembersRequest(List.of("아무나"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_INVITE_TO_DIRECT_ROOM);
    }

    @Test
    void inviteMembers_친구가_아니면_예외() {
        // given
        ChatRoom groupRoom = room(roomId, ChatRoomType.GROUP, true);
        User invitee = testUser(targetId, "남남");
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(groupRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, adminId))
                .willReturn(Optional.of(member(groupRoom, adminId, ChatRoomRole.ADMIN)));
        given(userRepository.findByNickname("남남")).willReturn(Optional.of(invitee));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, targetId))
                .willReturn(Optional.empty());
        given(friendRepository.findRelationBetween(adminId, targetId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> chatRoomService.inviteMembers(adminId, roomId,
                new InviteMembersRequest(List.of("남남"))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHATROOM_INVITE_NOT_FRIEND);
    }

    // ── kickMember() ───────────────────────────────────────────

    @Test
    void kickMember_ADMIN이_정상적으로_추방한다() {
        // given
        ChatRoom openRoom = room(roomId, ChatRoomType.OPEN, true);
        ChatRoomMember target = member(openRoom, targetId, ChatRoomRole.MEMBER);
        User targetUser = testUser(targetId, "추방대상");
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(openRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, adminId))
                .willReturn(Optional.of(member(openRoom, adminId, ChatRoomRole.ADMIN)));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, targetId))
                .willReturn(Optional.of(target));
        given(userRepository.findById(targetId)).willReturn(Optional.of(targetUser));

        // when
        ChatRoomKickResponse response = chatRoomService.kickMember(adminId, roomId, targetId);

        // then
        assertThat(response.message()).contains("추방대상");
        assertThat(target.isActive()).isFalse();
    }

    @Test
    void kickMember_ADMIN이_아니면_예외() {
        // given
        ChatRoom openRoom = room(roomId, ChatRoomType.OPEN, true);
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(openRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, adminId))
                .willReturn(Optional.of(member(openRoom, adminId, ChatRoomRole.MEMBER)));

        // when & then
        assertThatThrownBy(() -> chatRoomService.kickMember(adminId, roomId, targetId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_CHATROOM_ADMIN);
    }

    @Test
    void kickMember_본인을_추방대상으로_지정하면_예외() {
        // given
        ChatRoom openRoom = room(roomId, ChatRoomType.OPEN, true);
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(openRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, adminId))
                .willReturn(Optional.of(member(openRoom, adminId, ChatRoomRole.ADMIN)));

        // when & then
        assertThatThrownBy(() -> chatRoomService.kickMember(adminId, roomId, adminId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_KICK_YOURSELF);
    }

    // ── changeAdmin() ──────────────────────────────────────────

    @Test
    void changeAdmin_정상_위임시_기존관리자는_강등되고_신규관리자는_승격된다() {
        // given
        ChatRoom groupRoom = room(roomId, ChatRoomType.GROUP, true);
        ChatRoomMember current = member(groupRoom, adminId, ChatRoomRole.ADMIN);
        ChatRoomMember newAdmin = member(groupRoom, targetId, ChatRoomRole.MEMBER);
        User prevUser = testUser(adminId, "이전관리자");
        User newUser = testUser(targetId, "신규관리자");
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(groupRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, adminId))
                .willReturn(Optional.of(current));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, targetId))
                .willReturn(Optional.of(newAdmin));
        given(userRepository.findById(adminId)).willReturn(Optional.of(prevUser));
        given(userRepository.findById(targetId)).willReturn(Optional.of(newUser));

        // when
        ChatRoomAdminResponse response = chatRoomService.changeAdmin(adminId, roomId, new ChangeAdminRequest(targetId));

        // then
        assertThat(response.message()).contains("이전관리자").contains("신규관리자");
        assertThat(current.getRole()).isEqualTo(ChatRoomRole.MEMBER);
        assertThat(newAdmin.getRole()).isEqualTo(ChatRoomRole.ADMIN);
    }

    @Test
    void changeAdmin_DIRECT는_관리자_개념이_없어_예외() {
        // given
        ChatRoom directRoom = room(roomId, ChatRoomType.DIRECT, true);
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(directRoom));

        // when & then
        assertThatThrownBy(() -> chatRoomService.changeAdmin(adminId, roomId, new ChangeAdminRequest(targetId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_CHANGE_ADMIN);
    }

    // ── leaveRoom() ────────────────────────────────────────────

    @Test
    void leaveRoom_DIRECT는_제약없이_나간다() {
        // given
        ChatRoom directRoom = room(roomId, ChatRoomType.DIRECT, true);
        ChatRoomMember me = member(directRoom, adminId, ChatRoomRole.MEMBER);
        ChatRoomMember other = member(directRoom, targetId, ChatRoomRole.MEMBER);
        User otherUser = testUser(targetId, "상대방");
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(directRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, adminId))
                .willReturn(Optional.of(me));
        given(chatRoomMemberRepository.findByChatRoomIdAndIsActiveTrue(roomId)).willReturn(List.of(me, other));
        given(userRepository.findById(targetId)).willReturn(Optional.of(otherUser));

        // when
        ChatRoomLeaveResponse response = chatRoomService.leaveRoom(adminId, roomId);

        // then
        assertThat(response.message()).contains("상대방");
        assertThat(me.isActive()).isFalse();
        assertThat(directRoom.isActive()).isTrue(); // 상대방 쪽엔 영향 없음
    }

    @Test
    void leaveRoom_GROUP에서_ADMIN이_혼자가_아니면_위임을_먼저_해야한다() {
        // given
        ChatRoom groupRoom = room(roomId, ChatRoomType.GROUP, true);
        ChatRoomMember admin = member(groupRoom, adminId, ChatRoomRole.ADMIN);
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(groupRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, adminId))
                .willReturn(Optional.of(admin));
        given(chatRoomMemberRepository.countByChatRoomIdAndIsActiveTrue(roomId)).willReturn(2);

        // when & then
        assertThatThrownBy(() -> chatRoomService.leaveRoom(adminId, roomId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CHATROOM_ADMIN_MUST_DELEGATE);
    }

    @Test
    void leaveRoom_GROUP에서_ADMIN_혼자_남았으면_나가면서_방도_비활성화한다() {
        // given
        ChatRoom groupRoom = room(roomId, ChatRoomType.GROUP, true);
        ChatRoomMember admin = member(groupRoom, adminId, ChatRoomRole.ADMIN);
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(groupRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, adminId))
                .willReturn(Optional.of(admin));
        given(chatRoomMemberRepository.countByChatRoomIdAndIsActiveTrue(roomId)).willReturn(1);

        // when
        chatRoomService.leaveRoom(adminId, roomId);

        // then
        assertThat(admin.isActive()).isFalse();
        assertThat(groupRoom.isActive()).isFalse();
    }

    // ── deleteRoom() ───────────────────────────────────────────

    @Test
    void deleteRoom_ADMIN이_정상적으로_삭제하면_전원_강제퇴장된다() {
        // given
        ChatRoom groupRoom = room(roomId, ChatRoomType.GROUP, true);
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(groupRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, adminId))
                .willReturn(Optional.of(member(groupRoom, adminId, ChatRoomRole.ADMIN)));

        // when
        ChatRoomDeleteResponse response = chatRoomService.deleteRoom(adminId, roomId);

        // then
        assertThat(response.message()).contains("삭제");
        assertThat(groupRoom.isActive()).isFalse();
        org.mockito.Mockito.verify(chatRoomMemberRepository).deactivateAllByChatRoomId(roomId);
    }

    @Test
    void deleteRoom_DIRECT는_삭제_불가() {
        // given
        ChatRoom directRoom = room(roomId, ChatRoomType.DIRECT, true);
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(directRoom));

        // when & then
        assertThatThrownBy(() -> chatRoomService.deleteRoom(adminId, roomId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_DELETE_DIRECT_ROOM);
    }

    @Test
    void deleteRoom_ADMIN이_아니면_예외() {
        // given
        ChatRoom groupRoom = room(roomId, ChatRoomType.GROUP, true);
        given(chatRoomRepository.findById(roomId)).willReturn(Optional.of(groupRoom));
        given(chatRoomMemberRepository.findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, adminId))
                .willReturn(Optional.of(member(groupRoom, adminId, ChatRoomRole.MEMBER)));

        // when & then
        assertThatThrownBy(() -> chatRoomService.deleteRoom(adminId, roomId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_CHATROOM_ADMIN);
    }
}
