package com.example.noltok.block;

import com.example.noltok.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blocks", indexes = {
        @Index(name = "idx_blocks_blocker_blocked", columnList = "blocker_id, blocked_id"),
        @Index(name = "idx_blocks_blocked_blocker", columnList = "blocked_id, blocker_id")
})
// existsActiveBlockBetween()이 (blockerId,blockedId) OR (blockedId,blockerId)
// 형태의 양방향 조회라, Friend와 동일한 이유로 정방향/역방향 인덱스 둘 다 필요
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Block extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "blocker_id", nullable = false)
    private Long blockerId;

    @Column(name = "blocked_id", nullable = false)
    private Long blockedId;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    private Block(Long blockerId, Long blockedId) {
        this.blockerId = blockerId;
        this.blockedId = blockedId;
        this.isActive = true;
    }

    public static Block create(Long blockerId, Long blockedId) {
        return new Block(blockerId, blockedId);
    }

    // 재차단 시 기존 row 재활성화 (Soft Delete 원칙)
    public void reactivate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
