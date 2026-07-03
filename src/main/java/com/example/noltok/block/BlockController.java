package com.example.noltok.block;

import com.example.noltok.block.dto.request.BlockRequest;
import com.example.noltok.block.dto.response.BlockResponse;
import com.example.noltok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
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
}
