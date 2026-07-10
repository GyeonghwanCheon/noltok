package com.example.noltok.block;

import com.example.noltok.block.dto.request.BlockRequest;
import com.example.noltok.block.dto.response.BlockDeleteResponse;
import com.example.noltok.block.dto.response.BlockListResponse;
import com.example.noltok.block.dto.response.BlockResponse;
import com.example.noltok.friend.Friend;
import com.example.noltok.friend.FriendRepository;
import com.example.noltok.friend.FriendStatus;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// DB 없이 순수 JVM에서 도는 단위 테스트 — Repository는 전부 Mock
@ExtendWith(MockitoExtension.class)
class BlockServiceTest {

    @Mock
    private BlockRepository blockRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private FriendRepository friendRepository;

    private BlockService blockService;

    private final Long userId = 1L;
    private final Long targetId = 2L;
    private final Long blockId = 100L;

    private Block persistedBlock(Long id, Long blockerId, Long blockedId, boolean active) {
        Block block = Block.create(blockerId, blockedId);
        if (!active) block.deactivate();
        ReflectionTestUtils.setField(block, "id", id);
        ReflectionTestUtils.setField(block, "updatedAt", LocalDateTime.now());
        return block;
    }

    private User testUser(Long id, String nickname) {
        User user = User.create(nickname + "@test.com", "encoded-pw", nickname);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    @BeforeEach
    void setUp() {
        blockService = new BlockService(blockRepository, userRepository, friendRepository);
    }

    // ── blockUser() ────────────────────────────────────────────

    @Test
    void blockUser_기존_차단이력이_없으면_신규_저장하고_친구관계를_삭제한다() {
        // given
        User target = testUser(targetId, "차단대상");
        Friend accepted = Friend.create(userId, targetId);
        accepted.accept();
        given(userRepository.findByNickname("차단대상")).willReturn(Optional.of(target));
        given(blockRepository.findByBlockerIdAndBlockedId(userId, targetId)).willReturn(Optional.empty());
        given(blockRepository.save(any(Block.class)))
                .willReturn(persistedBlock(blockId, userId, targetId, true));
        given(friendRepository.findRelationBetween(userId, targetId)).willReturn(Optional.of(accepted));

        // when
        BlockResponse response = blockService.blockUser(userId, new BlockRequest("차단대상"));

        // then
        assertThat(response.blockedNickname()).isEqualTo("차단대상");
        verify(blockRepository).save(any(Block.class));
        verify(friendRepository).delete(accepted);
    }

    @Test
    void blockUser_본인을_차단하려하면_예외() {
        // given
        User self = testUser(userId, "본인");
        given(userRepository.findByNickname("본인")).willReturn(Optional.of(self));

        // when & then
        assertThatThrownBy(() -> blockService.blockUser(userId, new BlockRequest("본인")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.CANNOT_BLOCK_YOURSELF);
        verify(blockRepository, never()).save(any());
    }

    @Test
    void blockUser_이미_차단중이면_예외() {
        // given
        User target = testUser(targetId, "차단대상");
        Block alreadyActive = persistedBlock(blockId, userId, targetId, true);
        given(userRepository.findByNickname("차단대상")).willReturn(Optional.of(target));
        given(blockRepository.findByBlockerIdAndBlockedId(userId, targetId)).willReturn(Optional.of(alreadyActive));

        // when & then
        assertThatThrownBy(() -> blockService.blockUser(userId, new BlockRequest("차단대상")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ALREADY_BLOCKED);
    }

    @Test
    void blockUser_과거에_해제된_차단이면_새로_저장하지_않고_재활성화한다() {
        // given
        User target = testUser(targetId, "차단대상");
        Block inactive = persistedBlock(blockId, userId, targetId, false);
        given(userRepository.findByNickname("차단대상")).willReturn(Optional.of(target));
        given(blockRepository.findByBlockerIdAndBlockedId(userId, targetId)).willReturn(Optional.of(inactive));
        given(friendRepository.findRelationBetween(userId, targetId)).willReturn(Optional.empty());

        // when
        blockService.blockUser(userId, new BlockRequest("차단대상"));

        // then: save()로 새 row를 만들지 않고 기존 row를 재활성화만 함
        assertThat(inactive.isActive()).isTrue();
        verify(blockRepository, never()).save(any());
    }

    @Test
    void blockUser_친구관계가_REJECTED면_삭제하지_않는다() {
        // given: 이미 끝난 관계라 이력 보존 (docs/decision-log.md 2026-07-03)
        User target = testUser(targetId, "차단대상");
        Friend rejected = Friend.create(userId, targetId);
        rejected.reject();
        given(userRepository.findByNickname("차단대상")).willReturn(Optional.of(target));
        given(blockRepository.findByBlockerIdAndBlockedId(userId, targetId)).willReturn(Optional.empty());
        given(blockRepository.save(any(Block.class)))
                .willReturn(persistedBlock(blockId, userId, targetId, true));
        given(friendRepository.findRelationBetween(userId, targetId)).willReturn(Optional.of(rejected));

        // when
        blockService.blockUser(userId, new BlockRequest("차단대상"));

        // then
        verify(friendRepository, never()).delete(any());
    }

    // ── getBlocks() ────────────────────────────────────────────

    @Test
    void getBlocks_활성_차단목록을_배치조회로_반환한다() {
        // given
        Block block1 = persistedBlock(blockId, userId, targetId, true);
        User blockedUser = testUser(targetId, "차단대상");
        given(blockRepository.findAllByBlockerIdAndIsActiveTrue(userId)).willReturn(List.of(block1));
        given(userRepository.findAllById(List.of(targetId))).willReturn(List.of(blockedUser));

        // when
        BlockListResponse response = blockService.getBlocks(userId);

        // then: 멤버 수만큼 findById를 반복하지 않고 findAllById 1번으로 처리 (N+1 방지)
        assertThat(response.blocks()).hasSize(1);
        assertThat(response.blocks().get(0).nickname()).isEqualTo("차단대상");
        verify(userRepository, never()).findById(any());
        verify(userRepository).findAllById(List.of(targetId));
    }

    // ── unblockUser() ──────────────────────────────────────────

    @Test
    void unblockUser_정상_해제시_Soft_Delete로_처리된다() {
        // given
        Block active = persistedBlock(blockId, userId, targetId, true);
        User blockedUser = testUser(targetId, "차단대상");
        given(blockRepository.findById(blockId)).willReturn(Optional.of(active));
        given(userRepository.findById(targetId)).willReturn(Optional.of(blockedUser));

        // when
        BlockDeleteResponse response = blockService.unblockUser(userId, blockId);

        // then: delete()가 아니라 deactivate()로 처리 (docs/decision-log.md 2026-07-02)
        assertThat(response.message()).contains("차단대상");
        assertThat(active.isActive()).isFalse();
        verify(blockRepository, never()).delete(any());
    }

    @Test
    void unblockUser_존재하지_않으면_예외() {
        // given
        given(blockRepository.findById(blockId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> blockService.unblockUser(userId, blockId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BLOCK_NOT_FOUND);
    }

    @Test
    void unblockUser_본인이_차단한_건이_아니면_예외() {
        // given: userId(1)가 아니라 다른 사람(999)이 차단한 건
        Block othersBlock = persistedBlock(blockId, 999L, targetId, true);
        given(blockRepository.findById(blockId)).willReturn(Optional.of(othersBlock));

        // when & then
        assertThatThrownBy(() -> blockService.unblockUser(userId, blockId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.NOT_BLOCK_OWNER);
    }
}
