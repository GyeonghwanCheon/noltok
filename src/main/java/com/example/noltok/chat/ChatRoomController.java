package com.example.noltok.chat;

import com.example.noltok.chat.dto.request.ChangeAdminRequest;
import com.example.noltok.chat.dto.request.CreateRoomRequest;
import com.example.noltok.chat.dto.request.InviteMembersRequest;
import com.example.noltok.chat.dto.request.JoinRoomRequest;
import com.example.noltok.chat.dto.response.*;
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

    // /search가 /{roomId} 보다 먼저 선언되어야 함
    // 이유: Spring은 경로를 위에서 아래로 매핑
    //       /{roomId} 가 먼저 있으면 "search"를 roomId로 인식할 수 있음
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ChatRoomSearchResponse>> searchRooms(
            @RequestParam String name) {
        ChatRoomSearchResponse response = chatRoomService.searchRooms(name);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<ChatRoomDetailResponse>> getRoomDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ChatRoomDetailResponse response = chatRoomService.getRoomDetail(userId, roomId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{roomId}/join")
    public ResponseEntity<ApiResponse<ChatRoomJoinResponse>> joinRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId,
            @RequestBody(required = false) JoinRoomRequest request) {
        Long userId = Long.parseLong(userDetails.getUsername());
        String password = request != null ? request.password() : null;
        ChatRoomJoinResponse response = chatRoomService.joinRoom(userId, roomId, password);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<ChatRoomInviteResponse>> inviteMembers(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId,
            @Valid @RequestBody InviteMembersRequest request) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ChatRoomInviteResponse response = chatRoomService.inviteMembers(userId, roomId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{roomId}/members/{targetUserId}")
    public ResponseEntity<ApiResponse<ChatRoomKickResponse>> kickMember(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId,
            @PathVariable Long targetUserId) {
        Long adminUserId = Long.parseLong(userDetails.getUsername());
        ChatRoomKickResponse response = chatRoomService.kickMember(adminUserId, roomId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{roomId}/admin")
    public ResponseEntity<ApiResponse<ChatRoomAdminResponse>> changeAdmin(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId,
            @Valid @RequestBody ChangeAdminRequest request) {
        Long currentAdminUserId = Long.parseLong(userDetails.getUsername());
        ChatRoomAdminResponse response = chatRoomService.changeAdmin(currentAdminUserId, roomId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<ChatRoomLeaveResponse>> leaveRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ChatRoomLeaveResponse response = chatRoomService.leaveRoom(userId, roomId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
