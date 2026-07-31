package com.example.noltok.block.dto.response;

import com.example.noltok.block.dto.BlockDto;

import java.util.List;

public record BlockListResponse(
        List<BlockDto> blocks,
        boolean hasNext,
        Long nextCursor
) {
    // nextCursor는 목록의 마지막(가장 오래된) 차단의 blockId (id DESC 정렬 기준)
    public static BlockListResponse of(List<BlockDto> blocks, boolean hasNext) {
        Long nextCursor = blocks.isEmpty() ? null : blocks.get(blocks.size() - 1).blockId();
        return new BlockListResponse(blocks, hasNext, nextCursor);
    }
}
