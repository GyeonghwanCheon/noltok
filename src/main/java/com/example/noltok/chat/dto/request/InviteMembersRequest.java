package com.example.noltok.chat.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record InviteMembersRequest(

        @NotEmpty(message = "초대할 유저 닉네임은 1명 이상이어야 합니다.")
        List<String> nicknames

) {}
