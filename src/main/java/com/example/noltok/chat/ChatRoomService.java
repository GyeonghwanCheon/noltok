package com.example.noltok.chat;

import com.example.noltok.chat.dto.request.CreateRoomRequest;
import com.example.noltok.chat.dto.response.ChatRoomListResponse;
import com.example.noltok.chat.dto.response.ChatRoomSummaryDto;
import com.example.noltok.chat.dto.response.ChatRoomResponse;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public ChatRoomResponse createRoom(Long userId, CreateRoomRequest request) {

        // DIRECT 검증
        if (request.type() == ChatRoomType.DIRECT) {
            validateDirectRoom(userId, request.nicknames());
        }

        // GROUP 이름 검증
        if (request.type() == ChatRoomType.GROUP &&
                (request.roomname() == null || request.roomname().isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        // 초대할 유저 조회
        List<User> invitedUsers = new ArrayList<>();
        for (String nickname : request.nicknames()) {
            User user = userRepository.findByNickname(nickname)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            // 1. 본인 닉네임 포함 여부 체크
            // → 생성자는 자동으로 ADMIN으로 등록되므로
            //   nicknames에 본인이 포함되면 중복 INSERT 발생
            if (user.getId().equals(userId)) {
                throw new BusinessException(ErrorCode.CANNOT_INVITE_YOURSELF);
            }

            // 2. nicknames 중복 체크
            // → 같은 닉네임이 두 번 들어오면 중복 INSERT 발생
            boolean isDuplicate = invitedUsers.stream()
                    .anyMatch(u -> u.getId().equals(user.getId()));
            if (isDuplicate) {
                throw new BusinessException(ErrorCode.DUPLICATE_INVITE_NICKNAME);
            }

            invitedUsers.add(user);
        }

        // ChatRoom 생성
        ChatRoom chatRoom = ChatRoom.create(request.roomname(), request.type(), userId);
        chatRoomRepository.save(chatRoom);

        // 생성자 ADMIN 저장
        ChatRoomMember adminMember = ChatRoomMember.create(chatRoom, userId, ChatRoomRole.ADMIN);
        chatRoomMemberRepository.save(adminMember);

        // 초대 유저 MEMBER 저장
        for (User invitedUser : invitedUsers) {
            ChatRoomMember member = ChatRoomMember.create(
                    chatRoom, invitedUser.getId(), ChatRoomRole.MEMBER);
            chatRoomMemberRepository.save(member);
        }

        int memberCount = 1 + invitedUsers.size();
        return ChatRoomResponse.of(chatRoom, memberCount, ChatRoomRole.ADMIN);
    }

    // 내 채팅방 목록 조회
    // readOnly = true 이유: 조회만 하는 메서드, 변경감지 생략으로 성능 최적화
    @Transactional(readOnly = true)
    public ChatRoomListResponse getMyRooms(Long userId) {

        // 1. 내가 활성 멤버로 있는 채팅방 멤버십 전체 조회
        List<ChatRoomMember> myMemberships = chatRoomMemberRepository
                .findActiveRoomsByUserId(userId);

        // 2. 각 멤버십에서 채팅방 정보 + 내 역할 추출
        // → updatedAt 기준 내림차순 정렬 (최근 활동 순)
        List<ChatRoomSummaryDto> rooms = myMemberships.stream()
                .map(member -> ChatRoomSummaryDto.of(member.getChatRoom(), member))
                .sorted((a, b) -> b.updatedAt().compareTo(a.updatedAt()))
                .toList();

        return ChatRoomListResponse.of(rooms);
    }



    // DIRECT 채팅방 전용 검증
    // → 1명만 초대 가능
    // → 이미 해당 유저와의 DIRECT 방이 있으면 중복 생성 불가
    private void validateDirectRoom(Long userId, List<String> nicknames) {
        if (nicknames.size() != 1) {
            throw new BusinessException(ErrorCode.INVALID_DIRECT_ROOM_MEMBER_COUNT);
        }

        // 초대할 유저 조회
        User targetUser = userRepository.findByNickname(nicknames.get(0))
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 기존 DIRECT 방 중복 체크
        List<ChatRoom> existingRooms = chatRoomRepository
                .findDirectRoom(userId, targetUser.getId());
        if (!existingRooms.isEmpty()) {
            throw new BusinessException(ErrorCode.DIRECT_ROOM_ALREADY_EXISTS);
        }
    }
}
