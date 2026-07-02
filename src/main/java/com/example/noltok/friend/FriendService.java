package com.example.noltok.friend;

import com.example.noltok.friend.dto.request.FriendRequestRequest;
import com.example.noltok.friend.dto.response.FriendAcceptResponse;
import com.example.noltok.friend.dto.response.FriendRejectResponse;
import com.example.noltok.friend.dto.response.FriendRequestResponse;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendRepository friendRepository;
    private final UserRepository userRepository;

    @Transactional
    public FriendRequestResponse sendRequest(Long userId, FriendRequestRequest request) {
        User target = userRepository.findByNickname(request.nickname())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (target.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_REQUEST_YOURSELF);
        }

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
        Friend friend = friendRepository.findById(friendId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_NOT_FOUND));

        // 받은 사람만 수락 가능
        if (!friend.getReceiverId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FRIEND_REQUEST_RECEIVER);
        }

        if (friend.getStatus() != FriendStatus.PENDING) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED);
        }

        friend.accept();

        User requester = userRepository.findById(friend.getRequesterId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return FriendAcceptResponse.of(friend, requester.getNickname());
    }

    @Transactional
    public FriendRejectResponse rejectRequest(Long userId, Long friendId) {
        Friend friend = friendRepository.findById(friendId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FRIEND_NOT_FOUND));

        // 받은 사람만 거절 가능
        if (!friend.getReceiverId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_FRIEND_REQUEST_RECEIVER);
        }

        if (friend.getStatus() != FriendStatus.PENDING) {
            throw new BusinessException(ErrorCode.FRIEND_REQUEST_ALREADY_PROCESSED);
        }

        friend.reject();

        return FriendRejectResponse.of(friend);
    }
}
