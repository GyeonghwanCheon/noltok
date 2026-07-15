package com.example.noltok.friend;

import com.example.noltok.block.BlockRepository;
import com.example.noltok.friend.dto.request.FriendRequestRequest;
import com.example.noltok.friend.dto.response.FriendAcceptResponse;
import com.example.noltok.friend.dto.response.FriendCancelResponse;
import com.example.noltok.friend.dto.response.FriendDeleteResponse;
import com.example.noltok.friend.dto.response.FriendRejectResponse;
import com.example.noltok.friend.dto.response.FriendRequestResponse;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.notification.NotificationType;
import com.example.noltok.notification.kafka.NotificationProducer;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

// DB/Kafka 없이 순수 JVM에서 도는 단위 테스트 — Repository/Producer는 전부 Mock
// (Testcontainers 통합 테스트와는 목적이 다름: "쿼리 수"가 아니라 "분기·검증 로직"을 검증)
@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock
    private FriendRepository friendRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private NotificationProducer notificationProducer;

    private FriendService friendService;

    private final Long userId = 1L;
    private final Long targetId = 2L;
    private final Long friendId = 100L;

    // Friend.create()로 만든 엔티티는 실제 JPA 영속화를 거치지 않아
    // id/updatedAt이 null이라, BaseEntity의 감사(auditing) 필드를
    // 리플렉션으로 채워 "이미 저장된 것처럼" 만드는 헬퍼
    private Friend persistedFriend(Long id, Long requesterId, Long receiverId, FriendStatus status) {
        Friend friend = Friend.create(requesterId, receiverId);
        if (status == FriendStatus.ACCEPTED) friend.accept();
        if (status == FriendStatus.REJECTED) friend.reject();
        ReflectionTestUtils.setField(friend, "id", id);
        ReflectionTestUtils.setField(friend, "updatedAt", LocalDateTime.now());
        return friend;
    }

    private User testUser(Long id, String nickname) {
        User user = User.create(nickname + "@test.com", "encoded-pw", nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        friendService = new FriendService(friendRepository, userRepository, blockRepository, notificationProducer);
    }

    // ── sendRequest() ──────────────────────────────────────────

    @Test
    void sendRequest_기존_관계가_없으면_신규_저장하고_알림을_발행한다() {
        // given
        User requester = testUser(userId, "요청자");
        User target = testUser(targetId, "수신자");
        given(userRepository.findByNickname("수신자")).willReturn(Optional.of(target));
        given(blockRepository.existsActiveBlockBetween(userId, targetId)).willReturn(false);
        given(friendRepository.findRelationBetween(userId, targetId)).willReturn(Optional.empty());
        given(friendRepository.save(any(Friend.class)))
                .willReturn(persistedFriend(friendId, userId, targetId, FriendStatus.PENDING));
        given(userRepository.findById(userId)).willReturn(Optional.of(requester));

        // when
        FriendRequestResponse response = friendService.sendRequest(userId, new FriendRequestRequest("수신자"));

        // then
        assertThat(response.friendId()).isEqualTo(friendId);
        assertThat(response.status()).isEqualTo("PENDING");
        verify(friendRepository).save(any(Friend.class));
        verify(notificationProducer).publish(targetId, NotificationType.FRIEND_REQUEST,
                "요청자님이 친구 요청을 보냈습니다.");
    }

    @Test
    void sendRequest_본인에게_요청하면_예외() {
        // given
        User self = testUser(userId, "본인");
        given(userRepository.findByNickname("본인")).willReturn(Optional.of(self));

        // when & then
        assertThatThrownBy(() -> friendService.sendRequest(userId, new FriendRequestRequest("본인")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_REQUEST_YOURSELF);
        verify(friendRepository, never()).save(any());
    }

    @Test
    void sendRequest_차단_관계면_예외이고_저장되지_않는다() {
        // given
        User target = testUser(targetId, "차단상대");
        given(userRepository.findByNickname("차단상대")).willReturn(Optional.of(target));
        given(blockRepository.existsActiveBlockBetween(userId, targetId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> friendService.sendRequest(userId, new FriendRequestRequest("차단상대")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FRIEND_REQUEST_BLOCKED);
        verify(friendRepository, never()).save(any());
    }

    @Test
    void sendRequest_기존_관계가_REJECTED면_새로_저장하지_않고_기존_row를_재사용한다() {
        // given
        User requester = testUser(userId, "요청자");
        User target = testUser(targetId, "수신자");
        Friend rejected = persistedFriend(friendId, targetId, userId, FriendStatus.REJECTED); // 방향이 반대였던 이전 요청
        given(userRepository.findByNickname("수신자")).willReturn(Optional.of(target));
        given(blockRepository.existsActiveBlockBetween(userId, targetId)).willReturn(false);
        given(friendRepository.findRelationBetween(userId, targetId)).willReturn(Optional.of(rejected));
        given(userRepository.findById(userId)).willReturn(Optional.of(requester));

        // when
        FriendRequestResponse response = friendService.sendRequest(userId, new FriendRequestRequest("수신자"));

        // then: 새 row를 만들지 않고(reopen만 호출) 기존 row 그대로 재사용
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(rejected.getRequesterId()).isEqualTo(userId);
        assertThat(rejected.getReceiverId()).isEqualTo(targetId);
        verify(friendRepository, never()).save(any());
    }

    // ── acceptRequest() ────────────────────────────────────────

    @Test
    void acceptRequest_정상_수락() {
        // given
        Friend pending = persistedFriend(friendId, targetId, userId, FriendStatus.PENDING);
        User requester = testUser(targetId, "요청자");
        given(friendRepository.findById(friendId)).willReturn(Optional.of(pending));
        given(userRepository.findById(targetId)).willReturn(Optional.of(requester));

        // when
        FriendAcceptResponse response = friendService.acceptRequest(userId, friendId);

        // then
        assertThat(response.status()).isEqualTo("ACCEPTED");
        assertThat(pending.getStatus()).isEqualTo(FriendStatus.ACCEPTED);
    }

    @Test
    void acceptRequest_받은_사람이_아니면_예외() {
        // given: userId(1)가 아니라 다른 사람(999)이 받은 요청
        Friend pending = persistedFriend(friendId, targetId, 999L, FriendStatus.PENDING);
        given(friendRepository.findById(friendId)).willReturn(Optional.of(pending));

        // when & then
        assertThatThrownBy(() -> friendService.acceptRequest(userId, friendId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FRIEND_REQUEST_RECEIVER);
    }

    // ── rejectRequest() ────────────────────────────────────────

    @Test
    void rejectRequest_정상_거절() {
        // given
        Friend pending = persistedFriend(friendId, targetId, userId, FriendStatus.PENDING);
        given(friendRepository.findById(friendId)).willReturn(Optional.of(pending));

        // when
        FriendRejectResponse response = friendService.rejectRequest(userId, friendId);

        // then
        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(pending.getStatus()).isEqualTo(FriendStatus.REJECTED);
    }

    @Test
    void rejectRequest_이미_처리된_요청이면_예외() {
        // given: 이미 ACCEPTED된 요청을 다시 거절 시도
        Friend accepted = persistedFriend(friendId, targetId, userId, FriendStatus.ACCEPTED);
        given(friendRepository.findById(friendId)).willReturn(Optional.of(accepted));

        // when & then
        assertThatThrownBy(() -> friendService.rejectRequest(userId, friendId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED);
    }

    // ── deleteFriend() ─────────────────────────────────────────

    @Test
    void deleteFriend_정상_삭제시_Hard_Delete로_처리된다() {
        // given
        Friend accepted = persistedFriend(friendId, userId, targetId, FriendStatus.ACCEPTED);
        User friendUser = testUser(targetId, "친구");
        given(friendRepository.findById(friendId)).willReturn(Optional.of(accepted));
        given(userRepository.findById(targetId)).willReturn(Optional.of(friendUser));

        // when
        FriendDeleteResponse response = friendService.deleteFriend(userId, friendId);

        // then: Soft Delete(isActive)가 아니라 실제 delete() 호출
        assertThat(response.message()).contains("친구");
        verify(friendRepository, times(1)).delete(accepted);
    }

    @Test
    void deleteFriend_당사자가_아니면_예외() {
        // given: userId(1)는 이 관계(999 ↔ 998)의 당사자가 아님
        Friend accepted = persistedFriend(friendId, 999L, 998L, FriendStatus.ACCEPTED);
        given(friendRepository.findById(friendId)).willReturn(Optional.of(accepted));

        // when & then
        assertThatThrownBy(() -> friendService.deleteFriend(userId, friendId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FRIEND_MEMBER);
        verify(friendRepository, never()).delete(any());
    }

    // ── cancelRequest() ────────────────────────────────────────

    @Test
    void cancelRequest_정상_취소() {
        // given
        Friend pending = persistedFriend(friendId, userId, targetId, FriendStatus.PENDING);
        User receiver = testUser(targetId, "수신자");
        given(friendRepository.findById(friendId)).willReturn(Optional.of(pending));
        given(userRepository.findById(targetId)).willReturn(Optional.of(receiver));

        // when
        FriendCancelResponse response = friendService.cancelRequest(userId, friendId);

        // then
        assertThat(response.message()).contains("취소");
        verify(friendRepository, times(1)).delete(pending);
    }

    @Test
    void cancelRequest_보낸_사람이_아니면_예외() {
        // given: userId(1)가 아니라 다른 사람(999)이 보낸 요청
        Friend pending = persistedFriend(friendId, 999L, targetId, FriendStatus.PENDING);
        given(friendRepository.findById(friendId)).willReturn(Optional.of(pending));

        // when & then
        assertThatThrownBy(() -> friendService.cancelRequest(userId, friendId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_FRIEND_REQUEST_SENDER);
        verify(friendRepository, never()).delete(any());
    }
}
