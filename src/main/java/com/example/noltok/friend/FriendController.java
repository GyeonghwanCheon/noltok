package com.example.noltok.friend;

import com.example.noltok.friend.dto.request.FriendRequestRequest;
import com.example.noltok.friend.dto.response.FriendAcceptResponse;
import com.example.noltok.friend.dto.response.FriendListResponse;
import com.example.noltok.friend.dto.response.FriendReceivedListResponse;
import com.example.noltok.friend.dto.response.FriendRejectResponse;
import com.example.noltok.friend.dto.response.FriendRequestResponse;
import com.example.noltok.friend.dto.response.FriendSentListResponse;
import com.example.noltok.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<FriendRequestResponse>> sendRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody FriendRequestRequest request) {

        Long userId = Long.parseLong(userDetails.getUsername());
        FriendRequestResponse response = friendService.sendRequest(userId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("친구 요청을 보냈습니다.", response));
    }

    @PatchMapping("/{friendId}/accept")
    public ResponseEntity<ApiResponse<FriendAcceptResponse>> acceptRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long friendId) {

        Long userId = Long.parseLong(userDetails.getUsername());
        FriendAcceptResponse response = friendService.acceptRequest(userId, friendId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PatchMapping("/{friendId}/reject")
    public ResponseEntity<ApiResponse<FriendRejectResponse>> rejectRequest(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long friendId) {

        Long userId = Long.parseLong(userDetails.getUsername());
        FriendRejectResponse response = friendService.rejectRequest(userId, friendId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<FriendListResponse>> getFriends(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = Long.parseLong(userDetails.getUsername());
        FriendListResponse response = friendService.getFriends(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/received")
    public ResponseEntity<ApiResponse<FriendReceivedListResponse>> getReceivedRequests(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = Long.parseLong(userDetails.getUsername());
        FriendReceivedListResponse response = friendService.getReceivedRequests(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<FriendSentListResponse>> getSentRequests(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = Long.parseLong(userDetails.getUsername());
        FriendSentListResponse response = friendService.getSentRequests(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
