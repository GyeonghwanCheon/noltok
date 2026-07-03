package com.example.noltok.block.dto.response;

import com.example.noltok.block.Block;

import java.time.LocalDate;

public record BlockResponse(
        Long blockId,
        Long blockedId,
        String blockedNickname,
        LocalDate blockedAt
) {
    // blockedAt은 updatedAt 기준 (재차단 시 "이번 차단 시점" 반영, Friend와 동일 원칙)
    public static BlockResponse of(Block block, String blockedNickname) {
        return new BlockResponse(
                block.getId(),
                block.getBlockedId(),
                blockedNickname,
                block.getUpdatedAt().toLocalDate()
        );
    }
}
