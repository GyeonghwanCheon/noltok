package com.example.noltok.block;

import com.example.noltok.block.dto.BlockDto;
import com.example.noltok.block.dto.request.BlockRequest;
import com.example.noltok.block.dto.response.BlockDeleteResponse;
import com.example.noltok.block.dto.response.BlockListResponse;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

    // 차단은 "이 사람과의 모든 연결을 끊겠다"는 의도라 진행 중인 친구 요청도 정리 (REJECTED는 제외)
    private void removeExistingFriendship(Long userId, Long targetId) {
        friendRepository.findRelationBetween(userId, targetId)
                .filter(friend -> friend.getStatus() != FriendStatus.REJECTED)
                .ifPresent(friendRepository::delete);
    }

    @Transactional(readOnly = true)
    public BlockListResponse getBlocks(Long userId) {
        // 1. 활성 차단 목록 조회
        List<Block> activeBlocks = blockRepository.findAllByBlockerIdAndIsActiveTrue(userId);

        // 2. 차단 대상 userId 추출
        List<Long> blockedIds = activeBlocks.stream()
                .map(Block::getBlockedId)
                .toList();

        // 3. 대상 유저 정보 일괄 조회 (N+1 방지)
        Map<Long, User> userMap = userRepository.findAllById(blockedIds).stream()
                .collect(Collectors.toMap(User::getId, user -> user));

        // 4. DTO 변환
        List<BlockDto> blocks = activeBlocks.stream()
                .map(block -> BlockDto.of(block, userMap.get(block.getBlockedId())))
                .toList();

        return BlockListResponse.of(blocks);
    }

    @Transactional
    public BlockDeleteResponse unblockUser(Long userId, Long blockId) {
        // 1. blockId로 조회, 활성 차단만 대상
        Block block = blockRepository.findById(blockId)
                .filter(Block::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.BLOCK_NOT_FOUND));

        // 2. 본인이 차단한 건만 해제 가능
        if (!block.getBlockerId().equals(userId)) {
            throw new BusinessException(ErrorCode.NOT_BLOCK_OWNER);
        }

        // 3. 응답 메시지에 넣을 상대방 닉네임 조회
        User blockedUser = userRepository.findById(block.getBlockedId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 4. Soft Delete
        block.deactivate();

        return BlockDeleteResponse.of(blockId, blockedUser.getNickname());
    }
}
