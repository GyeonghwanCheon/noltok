package com.example.noltok.user;

import com.example.noltok.global.response.ApiResponse;
import com.example.noltok.user.dto.request.ChangePasswordRequest;
import com.example.noltok.user.dto.request.UpdateProfileRequest;
import com.example.noltok.user.dto.response.DeleteAccountResponse;
import com.example.noltok.user.dto.response.UserResponse;
import com.example.noltok.user.dto.response.UserSummaryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMyInfo(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = Long.parseLong(userDetails.getUsername());
        UserResponse response = userService.getMyInfo(userId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }


    @PatchMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyInfo(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequest request) {

        Long userId = Long.parseLong(userDetails.getUsername());
        UserResponse response = userService.updateMyInfo(userId, request);

        return ResponseEntity.ok(ApiResponse.ok("회원 정보가 수정되었습니다.", response));
    }


    @PatchMapping("/me/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequest request) {
        Long userId = Long.parseLong(userDetails.getUsername());
        userService.changePassword(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("비밀번호를 변경하였습니다.", null));
    }

    // 유저 검색
    // @RequestParam 이유:
    // → GET /api/v1/users?nickname=홍 형태로 쿼리 파라미터로 받음
    // → @PathVariable은 /users/{nickname} 형태 → 검색어가 경로에 노출되어 부적절
    @GetMapping
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> searchUsers(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam String nickname) {
        Long userId = Long.parseLong(userDetails.getUsername());
        List<UserSummaryResponse> response = userService.searchUsers(nickname, userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    // 유저 상세 조회
    // @PathVariable 이유:
    // → 특정 리소스(userId)를 식별하는 것이므로 경로에 포함하는 게 RESTful
    // → GET /api/v1/users/1 형태
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserSummaryResponse>> getUserDetail(
            @PathVariable Long userId) {
        UserSummaryResponse response = userService.getUserDetail(userId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<DeleteAccountResponse>> deleteMyAccount(
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        DeleteAccountResponse response = userService.deleteMyAccount(userId);
        return ResponseEntity.ok(ApiResponse.ok("회원 탈퇴 하였습니다.", response));
    }

}
