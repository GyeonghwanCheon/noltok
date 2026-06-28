package com.example.noltok.chat;

import com.example.noltok.chat.dto.request.CreateRoomRequest;
import com.example.noltok.chat.dto.response.ChatRoomListResponse;
import com.example.noltok.chat.dto.response.ChatRoomResponse;
import com.example.noltok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat/rooms")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatRoomResponse>> createRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateRoomRequest request) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ChatRoomResponse response = chatRoomService.createRoom(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("채팅방이 생성되었습니다.", response));
    }

    // GET /api/v1/chat/rooms
    // 채팅방 생성(POST)과 같은 경로지만 Method가 달라서 충돌 없음
    @GetMapping
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> getMyRooms(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ChatRoomListResponse response = chatRoomService.getMyRooms(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
