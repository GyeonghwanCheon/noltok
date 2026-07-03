package com.example.noltok.block.dto;

import com.example.noltok.block.Block;
import com.example.noltok.user.User;

import java.time.LocalDate;

public record BlockDto(
        Long blockId,
        Long userId,
        String nickname,
        String profileImageUrl,
        LocalDate blockedAt
) {
    public static BlockDto of(Block block, User blockedUser) {
        return new BlockDto(
                block.getId(),
                blockedUser.getId(),
                blockedUser.getNickname(),
                blockedUser.getProfileImageUrl(),
                block.getUpdatedAt().toLocalDate()
        );
    }
}
