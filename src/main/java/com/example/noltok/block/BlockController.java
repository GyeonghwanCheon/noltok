package com.example.noltok.block;

import com.example.noltok.block.dto.request.BlockRequest;
import com.example.noltok.block.dto.response.BlockDeleteResponse;
import com.example.noltok.block.dto.response.BlockListResponse;
import com.example.noltok.block.dto.response.BlockResponse;
import com.example.noltok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/blocks")
@RequiredArgsConstructor
public class BlockController {

    private final BlockService blockService;

    @PostMapping
    public ResponseEntity<ApiResponse<BlockResponse>> blockUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody BlockRequest request) {

        Long userId = Long.parseLong(userDetails.getUsername());
        BlockResponse response = blockService.blockUser(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<BlockListResponse>> getBlocks(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = Long.parseLong(userDetails.getUsername());
        BlockListResponse response = blockService.getBlocks(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{blockId}")
    public ResponseEntity<ApiResponse<BlockDeleteResponse>> unblockUser(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long blockId) {

        Long userId = Long.parseLong(userDetails.getUsername());
        BlockDeleteResponse response = blockService.unblockUser(userId, blockId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
