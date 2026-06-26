package com.example.noltok.auth;

import com.example.noltok.auth.dto.LoginRequest;
import com.example.noltok.auth.dto.LoginResponse;
import com.example.noltok.auth.dto.ReissueRequest;
import com.example.noltok.global.response.ApiResponse;
import com.example.noltok.user.dto.request.SignUpRequest;
import com.example.noltok.user.dto.response.SignUpResponse;
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
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // 회원가입
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignUpResponse>> signUp(
            @Valid @RequestBody SignUpRequest request) {

        SignUpResponse response = authService.signUp(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("회원가입이 완료되었습니다.", response));
    }

    // 로그인
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse response = authService.login(request);

        return ResponseEntity.ok(ApiResponse.ok("로그인 성공", response));
    }

    // 토큰 재발급
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<LoginResponse>> reissue(
            @Valid @RequestBody ReissueRequest request) {

        LoginResponse response = authService.reissue(request);

        return ResponseEntity.ok(ApiResponse.ok("토큰이 재발급되었습니다.", response));
    }

    // 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long userId = Long.parseLong(userDetails.getUsername());
        authService.logout(userId);

        return ResponseEntity.ok(ApiResponse.ok("로그아웃 완료", null));
    }
}
