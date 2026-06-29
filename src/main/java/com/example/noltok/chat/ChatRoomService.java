package com.example.noltok.chat;

import com.example.noltok.chat.dto.MemberDto;
import com.example.noltok.chat.dto.SearchRoomDto;
import com.example.noltok.chat.dto.request.CreateRoomRequest;
import com.example.noltok.chat.dto.response.*;
import com.example.noltok.chat.dto.ChatRoomSummaryDto;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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


    // getRoomDetail() 메서드만 추가, 기존 코드 유지
    @Transactional(readOnly = true)
    public ChatRoomDetailResponse getRoomDetail(Long userId, Long roomId) {

        // 1. 채팅방 존재 확인
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .filter(ChatRoom::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        // 2. 요청자가 멤버인지 확인
        // → 채팅방 존재 확인 후에 멤버 체크하는 이유:
        //   순서가 반대면 비멤버가 "채팅방이 존재하는지 여부"를 알 수 있음
        ChatRoomMember myMembership = chatRoomMemberRepository
                .findByChatRoomIdAndUserIdAndIsActiveTrue(roomId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_CHATROOM_MEMBER));

        // 3. 채팅방 전체 활성 멤버 목록 조회
        List<ChatRoomMember> members = chatRoomMemberRepository
                .findByChatRoomIdAndIsActiveTrue(roomId);

        // 4. 각 멤버의 userId로 User 정보 조회
        // ⚠️ N+1 발생 지점:
        // → 멤버 수만큼 SELECT 쿼리 발생
        // → 멤버가 100명이면 100번 쿼리
        // → 추후 userRepository.findAllById(userIds)로 한 번에 조회하는 방식으로 최적화 예정
        List<MemberDto> memberDtos = members.stream()
                .map(member -> {
                    User user = userRepository.findById(member.getUserId())
                            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                    return MemberDto.of(member, user);
                })
                .toList();

        return ChatRoomDetailResponse.of(chatRoom, myMembership, memberDtos);
    }

    // searchRooms() 메서드만 추가, 기존 코드 유지
    @Transactional(readOnly = true)
    public ChatRoomSearchResponse searchRooms(String name) {

        // 1. GROUP 채팅방 이름 부분일치 검색
        List<ChatRoom> chatRooms = chatRoomRepository.searchByRoomname(name);

        // 2. 각 채팅방의 활성 멤버 수 조회
        // ⚠️ N+1 발생 지점:
        // → 검색된 채팅방 수만큼 COUNT 쿼리 발생
        // → 추후 한 번의 쿼리로 최적화 예정
        List<SearchRoomDto> rooms = chatRooms.stream()
                .map(room -> {
                    int memberCount = chatRoomMemberRepository
                            .countByChatRoomIdAndIsActiveTrue(room.getId());
                    return SearchRoomDto.of(room, memberCount);
                })
                .toList();

        return ChatRoomSearchResponse.of(rooms);
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

    // joinRoom() 메서드만 추가, 기존 코드 유지
    @Transactional
    public ChatRoomJoinResponse joinRoom(Long userId, Long roomId) {

        // 1. 채팅방 존재 확인
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .filter(ChatRoom::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        // 2. DIRECT 채팅방 입장 불가
        // → DIRECT는 생성 시에만 멤버가 결정됨
        if (chatRoom.getType() == ChatRoomType.DIRECT) {
            throw new BusinessException(ErrorCode.CANNOT_INVITE_TO_DIRECT_ROOM);
        }

        // 3. 기존 멤버십 조회 (isActive 관계없이)
        Optional<ChatRoomMember> existingMember = chatRoomMemberRepository
                .findByChatRoomIdAndUserId(roomId, userId);

        if (existingMember.isPresent()) {
            ChatRoomMember member = existingMember.get();

            if (member.isActive()) {
                // 현재 활성 멤버 → 이미 입장 중
                throw new BusinessException(ErrorCode.ALREADY_CHATROOM_MEMBER);
            } else {
                // 나갔던 멤버 → 재입장 처리
                member.reactivate();
            }
        } else {
            // 신규 입장
            ChatRoomMember newMember = ChatRoomMember.create(chatRoom, userId, ChatRoomRole.MEMBER);
            chatRoomMemberRepository.save(newMember);
        }

        // 4. userId로 nickname 조회 후 메시지 생성
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return ChatRoomJoinResponse.of(roomId, ChatRoomRole.MEMBER.name(), user.getNickname());
    }

}
