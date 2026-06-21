package com.example.noltok.user;

import com.example.noltok.global.response.ApiResponse;
import com.example.noltok.user.dto.SignUpRequest;
import com.example.noltok.user.dto.SignUpResponse;
import com.example.noltok.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;


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



}
