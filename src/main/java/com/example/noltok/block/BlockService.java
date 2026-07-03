package com.example.noltok.block;

import com.example.noltok.block.dto.request.BlockRequest;
import com.example.noltok.block.dto.response.BlockResponse;
import com.example.noltok.friend.FriendRepository;
import com.example.noltok.friend.FriendStatus;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final BlockRepository blockRepository;
    private final UserRepository userRepository;
    private final FriendRepository friendRepository;

    @Transactional
    public BlockResponse blockUser(Long userId, BlockRequest request) {
        // 1. 닉네임으로 대상 유저 조회
        User target = userRepository.findByNickname(request.nickname())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 본인 여부 체크
        if (target.getId().equals(userId)) {
            throw new BusinessException(ErrorCode.CANNOT_BLOCK_YOURSELF);
        }

        // 3. 기존 차단 이력 조회 → 재활성화 또는 신규 생성
        Block block = blockRepository.findByBlockerIdAndBlockedId(userId, target.getId())
                .map(this::reactivateOrThrow)
                .orElseGet(() -> blockRepository.save(Block.create(userId, target.getId())));

        // 4. 기존 친구 관계(PENDING/ACCEPTED) 있으면 삭제
        removeExistingFriendship(userId, target.getId());

        return BlockResponse.of(block, target.getNickname());
    }

    private Block reactivateOrThrow(Block existing) {
        if (existing.isActive()) {
            throw new BusinessException(ErrorCode.ALREADY_BLOCKED);
        }
        existing.reactivate();
        return existing;
    }

    // 차단은 "이 사람과의 모든 연결을 끊겠다"는 의도라 진행 중인 요청도 함께 정리
    // → REJECTED는 이미 끝난 상태라 그대로 둠 (docs/decision-log.md 2026-07-03)
    private void removeExistingFriendship(Long userId, Long targetId) {
        friendRepository.findRelationBetween(userId, targetId)
                .filter(friend -> friend.getStatus() != FriendStatus.REJECTED)
                .ifPresent(friendRepository::delete);
    }
}
