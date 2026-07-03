package com.example.noltok.block.dto.response;

import com.example.noltok.block.dto.BlockDto;

import java.util.List;

public record BlockListResponse(
        List<BlockDto> blocks
) {
    public static BlockListResponse of(List<BlockDto> blocks) {
        return new BlockListResponse(blocks);
    }
}
