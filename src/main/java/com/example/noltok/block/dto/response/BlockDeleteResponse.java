package com.example.noltok.block.dto.response;

public record BlockDeleteResponse(
        Long blockId,
        String message
) {
    public static BlockDeleteResponse of(Long blockId, String blockedNickname) {
        return new BlockDeleteResponse(
                blockId,
                blockedNickname + "님 차단을 해제했습니다."
        );
    }
}
