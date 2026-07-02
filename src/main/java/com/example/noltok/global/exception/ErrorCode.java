package com.example.noltok.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    //Auth
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
    EXPIRED_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "만료된 Refresh Token입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    PASSWORD_NOT_MATCHED(HttpStatus.UNPROCESSABLE_ENTITY, "새 비밀번호와 확인 비밀번호가 일치하지 않습니다."),
    SAME_AS_CURRENT_PASSWORD(HttpStatus.UNPROCESSABLE_ENTITY, "현재 비밀번호와 동일한 비밀번호로 변경할 수 없습니다."),

    // ChatRoom
    CHATROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "채팅방을 찾을 수 없습니다."),
    NOT_CHATROOM_MEMBER(HttpStatus.FORBIDDEN, "채팅방 멤버가 아닙니다."),
    ALREADY_CHATROOM_MEMBER(HttpStatus.UNPROCESSABLE_ENTITY, "이미 채팅방 멤버입니다."),
    DIRECT_ROOM_ALREADY_EXISTS(HttpStatus.UNPROCESSABLE_ENTITY, "이미 해당 유저와의 1:1 채팅방이 존재합니다."),
    INVALID_DIRECT_ROOM_MEMBER_COUNT(HttpStatus.BAD_REQUEST, "1:1 채팅방은 상대방 1명만 초대할 수 있습니다."),
    NOT_CHATROOM_ADMIN(HttpStatus.FORBIDDEN, "채팅방 관리자만 사용할 수 있는 기능입니다."),
    CANNOT_INVITE_TO_DIRECT_ROOM(HttpStatus.FORBIDDEN, "1:1 채팅방에는 멤버를 초대할 수 없습니다."),
    CANNOT_INVITE_YOURSELF(HttpStatus.BAD_REQUEST, "본인을 채팅방에 초대할 수 없습니다."),
    DUPLICATE_INVITE_NICKNAME(HttpStatus.BAD_REQUEST, "초대 목록에 중복된 닉네임이 있습니다."),

    // Friend
    CANNOT_REQUEST_YOURSELF(HttpStatus.BAD_REQUEST, "본인에게 친구 요청을 보낼 수 없습니다."),
    FRIEND_REQUEST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 친구이거나 요청이 진행 중입니다."),
    FRIEND_NOT_FOUND(HttpStatus.NOT_FOUND, "친구 요청을 찾을 수 없습니다."),
    NOT_FRIEND_REQUEST_RECEIVER(HttpStatus.FORBIDDEN, "본인이 받은 요청이 아닙니다."),
    FRIEND_REQUEST_ALREADY_PROCESSED(HttpStatus.UNPROCESSABLE_ENTITY, "이미 처리된 요청입니다."),

    // Common
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 입력값입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String message;
}
