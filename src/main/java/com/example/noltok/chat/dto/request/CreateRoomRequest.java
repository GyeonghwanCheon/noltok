package com.example.noltok.chat.dto.request;

import com.example.noltok.chat.ChatRoomType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRoomRequest(

        // GROUP만 필수, DIRECT는 null 허용
        @Size(max = 100, message = "채팅방 이름은 100자 이하로 입력해주세요.")
        String roomname,

        @NotNull(message = "채팅방 타입은 필수입니다.")
        ChatRoomType type,

        @NotNull(message = "초대할 멤버를 입력해주세요.")
        @Size(min = 1, message = "최소 1명 이상 초대해야 합니다.")
        List<String> nicknames

) {}
