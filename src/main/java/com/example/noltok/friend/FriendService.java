package com.example.noltok.friend;

import com.example.noltok.friend.dto.FriendDto;
import com.example.noltok.friend.dto.ReceivedFriendRequestDto;
import com.example.noltok.friend.dto.SentFriendRequestDto;
import com.example.noltok.friend.dto.request.FriendRequestRequest;
import com.example.noltok.friend.dto.response.FriendAcceptResponse;
import com.example.noltok.friend.dto.response.FriendDeleteResponse;
import com.example.noltok.friend.dto.response.FriendListResponse;
import com.example.noltok.friend.dto.response.FriendReceivedListResponse;
import com.example.noltok.friend.dto.response.FriendRejectResponse;
import com.example.noltok.friend.dto.response.FriendRequestResponse;
import com.example.noltok.friend.dto.response.FriendSentListResponse;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

    @Transactional
    public FriendRequestResponse sendRequest(Long userId, FriendRequestRequest request) {
        // 1. 닉네임으로 대상 유저 조회
        User target = userRepository.findByNickname(request.nickname())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 본인 여부 체크
        if (target.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_REQUEST_YOURSELF);
        }

        // 3. 기존 관계 조회 → REJECTED면 재사용, 없으면 신규 생성
        Friend friend = friendRepository.findRelationBetween(userId, target.getId())
                .map(existing -> reuseIfRejected(existing, userId, target.getId()))
                .orElseGet(() -> friendRepository.save(Friend.create(userId, target.getId())));

        return FriendRequestResponse.of(friend, target.getNickname());
    }

    // 기존 관계가 REJECTED면 재사용(PENDING으로 전환), PENDING/ACCEPTED면 중복 요청으로 차단
    // → docs/decision-log.md 2026-07-02 결정
    // → existing은 findRelationBetween()으로 조회된 영속 상태 엔티티라
    //   reopen() 호출만으로 트랜잭션 커밋 시 자동 UPDATE (별도 save() 불필요)
    private Friend reuseIfRejected(Friend existing, Long requesterId, Long receiverId) {
        if (existing.getStatus() != FriendStatus.REJECTED) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_ALREADY_EXISTS);
        }
        existing.reopen(requesterId, receiverId);
        return existing;
    }

    @Transactional
    public FriendAcceptResponse acceptRequest(Long userId, Long friendId) {
        // 1. friendId로 요청 조회
        Friend friend = friendRepository.findById(friendId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_NOT_FOUND));

        // 2. 받은 사람만 수락 가능
        if (!friend.getReceiverId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FRIEND_REQUEST_RECEIVER);
        }

        // 3. PENDING 상태만 수락 가능
        if (friend.getStatus() != FriendStatus.PENDING) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED);
        }

        // 4. 상태 변경
        friend.accept();

        // 5. 응답 메시지에 넣을 요청자 닉네임 조회
        User requester = userRepository.findById(friend.getRequesterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return FriendAcceptResponse.of(friend, requester.getNickname());
    }

    @Transactional
    public FriendRejectResponse rejectRequest(Long userId, Long friendId) {
        // 1. friendId로 요청 조회
        Friend friend = friendRepository.findById(friendId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_NOT_FOUND));

        // 2. 받은 사람만 거절 가능
        if (!friend.getReceiverId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FRIEND_REQUEST_RECEIVER);
        }

        // 3. PENDING 상태만 거절 가능
        if (friend.getStatus() != FriendStatus.PENDING) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED);
        }

        // 4. 상태 변경
        friend.reject();

        return FriendRejectResponse.of(friend);
    }

    @Transactional(readOnly = true)
    public FriendListResponse getFriends(Long userId) {
        // 1. status=ACCEPTED, 내가 포함된 관계 전부 조회
        List<Friend> accepted = friendRepository.findAllAcceptedByUserId(userId);

        // 2. 각 관계에서 상대방 userId 추출 (requester/receiver 중 내가 아닌 쪽)
        List<Long> friendUserIds = accepted.stream()
                .map(f -> f.getRequesterId().equals(userId) ? f.getReceiverId() : f.getRequesterId())
                .toList();

        // 3. 상대방 유저 정보 일괄 조회 (N+1 방지, getRoomDetail()과 동일 패턴)
        Map<Long, User> userMap = userRepository.findAllById(friendUserIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 4. DTO 변환
        List<FriendDto> friends = accepted.stream()
                .map(f -> {
                    Long friendUserId = f.getRequesterId().equals(userId) ? f.getReceiverId() : f.getRequesterId();
                    return FriendDto.of(f, userMap.get(friendUserId));
                })
                .toList();

        return FriendListResponse.of(friends);
    }

    @Transactional(readOnly = true)
    public FriendReceivedListResponse getReceivedRequests(Long userId) {
        // 1. status=PENDING, receiverId=userId인 요청 전부 조회
        List<Friend> pending = friendRepository.findAllByReceiverIdAndStatus(userId, FriendStatus.PENDING);

        // 2. 요청자 userId 목록 추출
        List<Long> requesterIds = pending.stream()
                .map(Friend::getRequesterId)
                .toList();

        // 3. 요청자 유저 정보 일괄 조회 (N+1 방지)
        Map<Long, User> userMap = userRepository.findAllById(requesterIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 4. DTO 변환
        List<ReceivedFriendRequestDto> requests = pending.stream()
                .map(f -> ReceivedFriendRequestDto.of(f, userMap.get(f.getRequesterId())))
                .toList();

        return FriendReceivedListResponse.of(requests);
    }

    @Transactional(readOnly = true)
    public FriendSentListResponse getSentRequests(Long userId) {
        // 1. status=PENDING, requesterId=userId인 요청 전부 조회
        List<Friend> pending = friendRepository.findAllByRequesterIdAndStatus(userId, FriendStatus.PENDING);

        // 2. 수신자 userId 목록 추출
        List<Long> receiverIds = pending.stream()
                .map(Friend::getReceiverId)
                .toList();

        // 3. 수신자 유저 정보 일괄 조회 (N+1 방지)
        Map<Long, User> userMap = userRepository.findAllById(receiverIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 4. DTO 변환
        List<SentFriendRequestDto> requests = pending.stream()
                .map(f -> SentFriendRequestDto.of(f, userMap.get(f.getReceiverId())))
                .toList();

        return FriendSentListResponse.of(requests);
    }

    @Transactional
    public FriendDeleteResponse deleteFriend(Long userId, Long friendId) {
        // 1. friendId로 조회, ACCEPTED 상태만 삭제 대상
        Friend friend = friendRepository.findById(friendId)
                .filter(f -> f.getStatus() == FriendStatus.ACCEPTED)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_NOT_FOUND));

        // 2. 당사자(requester 또는 receiver)만 삭제 가능
        if (!friend.getRequesterId().equals(userId) && !friend.getReceiverId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FRIEND_MEMBER);
        }

        // 3. 응답 메시지에 넣을 상대방 닉네임 조회
        Long friendUserId = friend.getRequesterId().equals(userId) ? friend.getReceiverId() : friend.getRequesterId();
        User friendUser = userRepository.findById(friendUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 4. Hard Delete (docs/decision-log.md 2026-07-02 결정)
        friendRepository.delete(friend);

        return FriendDeleteResponse.of(friendId, friendUser.getNickname());
    }
}
