package com.example.noltok.block;

import com.example.noltok.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "blocks")
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

    // 재차단 시 기존 row 재활성화 (Soft Delete 원칙, docs/decision-log.md 2026-07-02)
    public void reactivate() {
        this.isActive = true;
    }

    public void deactivate() {
        this.isActive = false;
    }
}
