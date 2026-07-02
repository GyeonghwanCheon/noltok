package com.example.noltok.friend.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FriendRequestRequest(
        @NotBlank(message = "닉네임은 필수입니다.")
        String nickname
) {}
