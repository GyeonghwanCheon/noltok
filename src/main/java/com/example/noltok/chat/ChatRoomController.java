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

    // 채팅방 생성 API
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

    // 내 채팅방 목록 조회 API
    @GetMapping
    public ResponseEntity<ApiResponse<ChatRoomListResponse>> getMyRooms(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ChatRoomListResponse response = chatRoomService.getMyRooms(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 채팅방 검색 API — /{roomId}보다 먼저 선언해야 "search"가 roomId로 인식되지 않음
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<ChatRoomSearchResponse>> searchRooms(
            @RequestParam String name) {
        ChatRoomSearchResponse response = chatRoomService.searchRooms(name);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 채팅방 상세 조회 API
    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<ChatRoomDetailResponse>> getRoomDetail(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ChatRoomDetailResponse response = chatRoomService.getRoomDetail(userId, roomId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 채팅방 입장 API
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

    // 채팅방 멤버 초대 API
    @PostMapping("/{roomId}/members")
    public ResponseEntity<ApiResponse<ChatRoomInviteResponse>> inviteMembers(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId,
            @Valid @RequestBody InviteMembersRequest request) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ChatRoomInviteResponse response = chatRoomService.inviteMembers(userId, roomId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 채팅방 멤버 추방 API
    @DeleteMapping("/{roomId}/members/{targetUserId}")
    public ResponseEntity<ApiResponse<ChatRoomKickResponse>> kickMember(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId,
            @PathVariable Long targetUserId) {
        Long adminUserId = Long.parseLong(userDetails.getUsername());
        ChatRoomKickResponse response = chatRoomService.kickMember(adminUserId, roomId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 채팅방 관리자 변경 API
    @PatchMapping("/{roomId}/admin")
    public ResponseEntity<ApiResponse<ChatRoomAdminResponse>> changeAdmin(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId,
            @Valid @RequestBody ChangeAdminRequest request) {
        Long currentAdminUserId = Long.parseLong(userDetails.getUsername());
        ChatRoomAdminResponse response = chatRoomService.changeAdmin(currentAdminUserId, roomId, request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 채팅방 나가기 API
    @DeleteMapping("/{roomId}/leave")
    public ResponseEntity<ApiResponse<ChatRoomLeaveResponse>> leaveRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ChatRoomLeaveResponse response = chatRoomService.leaveRoom(userId, roomId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 채팅방 삭제 API
    @DeleteMapping("/{roomId}")
    public ResponseEntity<ApiResponse<ChatRoomDeleteResponse>> deleteRoom(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ChatRoomDeleteResponse response = chatRoomService.deleteRoom(userId, roomId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 채팅방 읽음 처리 API
    @PatchMapping("/{roomId}/read")
    public ResponseEntity<ApiResponse<ChatRoomReadResponse>> markAsRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long roomId) {
        Long userId = Long.parseLong(userDetails.getUsername());
        ChatRoomReadResponse response = chatRoomService.markAsRead(userId, roomId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
