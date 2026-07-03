package com.example.noltok.chat.dto.request;

import com.example.noltok.chat.ChatRoomType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateRoomRequest(

        // GROUP만 필수, DIRECT/OPEN/OPEN_PRIVATE는 null 허용
        @Size(max = 100, message = "채팅방 이름은 100자 이하로 입력해주세요.")
        String roomname,

        @NotNull(message = "채팅방 타입은 필수입니다.")
        ChatRoomType type,

        @Size(min = 4, message = "비밀번호는 4자리 이상이어야 합니다.")
        String password,
        // OPEN_PRIVATE 타입일 때만 사용, 그 외 타입에서는 무시됨

        // DIRECT/GROUP만 사용. nicknames 개수 필수 여부는 타입마다 달라서
        // (DIRECT 1명, GROUP 1명 이상, OPEN/OPEN_PRIVATE 불필요)
        // Bean Validation 대신 Service 단에서 타입별로 검증
        List<String> nicknames

) {}
