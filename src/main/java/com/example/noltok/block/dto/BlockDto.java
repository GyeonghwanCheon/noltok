package com.example.noltok.block.dto;

import com.example.noltok.block.Block;
import com.example.noltok.user.dto.UserProfileDto;

import java.time.LocalDate;

public record BlockDto(
        Long blockId,
        Long userId,
        String nickname,
        String profileImageUrl,
        LocalDate blockedAt
) {
    public static BlockDto of(Block block, UserProfileDto blockedUser) {
        return new BlockDto(
                block.getId(),
                blockedUser.userId(),
                blockedUser.nickname(),
                blockedUser.profileImageUrl(),
                block.getUpdatedAt().toLocalDate()
        );
    }
}
