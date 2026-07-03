package com.example.noltok.chat;

import com.example.noltok.block.BlockRepository;
import com.example.noltok.chat.dto.MemberDto;
import com.example.noltok.chat.dto.SearchRoomDto;
import com.example.noltok.chat.dto.request.CreateRoomRequest;
import com.example.noltok.chat.dto.response.*;
import com.example.noltok.chat.dto.ChatRoomSummaryDto;
import com.example.noltok.friend.FriendRepository;
import com.example.noltok.friend.FriendStatus;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.User;
import com.example.noltok.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private final FriendRepository friendRepository;
    private final BlockRepository blockRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public ChatRoomResponse createRoom(Long userId, CreateRoomRequest request) {
        List<String> nicknames = request.nicknames() != null ? request.nicknames() : List.of();

        // 1. 타입별 필수값 검증 (roomname / nicknames / password)
        validateRoomFields(request, nicknames);

        // 2. DIRECT 전용 검증 (1명 제한 + 기존 방 중복 체크)
        if (request.type() == ChatRoomType.DIRECT) {
            validateDirectRoom(userId, nicknames);
        }

        // 3. DIRECT/GROUP만 초대 대상 조회 + 친구·차단 검증
        // → OPEN/OPEN_PRIVATE는 nicknames 자체를 쓰지 않으므로 대상 없음
        List<User> invitedUsers = new ArrayList<>();
        if (request.type() == ChatRoomType.DIRECT || request.type() == ChatRoomType.GROUP) {
            for (String nickname : nicknames) {
                User user = userRepository.findByNickname(nickname)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

                // 본인 닉네임 포함 여부 체크
                if (user.getId().equals(userId)) {
                    throw new BusinessException(ErrorCode.CANNOT_INVITE_YOURSELF);
                }

                // nicknames 중복 체크
                boolean isDuplicate = invitedUsers.stream()
                        .anyMatch(u -> u.getId().equals(user.getId()));
                if (isDuplicate) {
                    throw new BusinessException(ErrorCode.DUPLICATE_INVITE_NICKNAME);
                }

                validateFriendAndBlock(userId, user.getId());

                invitedUsers.add(user);
            }
        }

        // 4. OPEN_PRIVATE면 password 암호화
        String encodedPassword = request.type() == ChatRoomType.OPEN_PRIVATE
                ? passwordEncoder.encode(request.password())
                : null;

        // 5. ChatRoom 생성
        ChatRoom chatRoom = ChatRoom.create(request.roomname(), request.type(), userId, encodedPassword);
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

    // 타입별 필수값 검증
    // → roomname: DIRECT만 제외하고 전부 필수
    // → nicknames: GROUP만 1명 이상 필수 (DIRECT는 validateDirectRoom에서 별도 체크)
    // → password: OPEN_PRIVATE만 필수
    private void validateRoomFields(CreateRoomRequest request, List<String> nicknames) {
        if (request.type() != ChatRoomType.DIRECT &&
                (request.roomname() == null || request.roomname().isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (request.type() == ChatRoomType.GROUP && nicknames.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        if (request.type() == ChatRoomType.OPEN_PRIVATE &&
                (request.password() == null || request.password().isBlank())) {
            throw new BusinessException(ErrorCode.CHATROOM_PASSWORD_REQUIRED);
        }
    }

    // 초대자(userId) 기준 친구/차단 검증
    // → 친구 검증을 먼저 해서, 친구가 아니면 차단 조회(추가 쿼리) 자체를 생략
    private void validateFriendAndBlock(Long userId, Long targetId) {
        boolean isFriend = friendRepository.findRelationBetween(userId, targetId)
                .map(friend -> friend.getStatus() == FriendStatus.ACCEPTED)
                .orElse(false);
        if (!isFriend) {
            throw new BusinessException(ErrorCode.CHATROOM_INVITE_NOT_FRIEND);
        }

        if (blockRepository.existsActiveBlockBetween(userId, targetId)) {
            throw new BusinessException(ErrorCode.CHATROOM_INVITE_BLOCKED);
        }
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
    public ChatRoomJoinResponse joinRoom(Long userId, Long roomId, String password) {

        // 1. 채팅방 존재 확인
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .filter(ChatRoom::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.CHATROOM_NOT_FOUND));

        // 2. DIRECT/GROUP은 이 API로 입장 불가 (초대로만 참여 가능)
        // → docs/decision-log.md 2026-07-03 결정
        if (chatRoom.getType() == ChatRoomType.DIRECT || chatRoom.getType() == ChatRoomType.GROUP) {
            throw new BusinessException(ErrorCode.CANNOT_SELF_JOIN_ROOM);
        }

        // 3. OPEN_PRIVATE 비밀번호 검증
        if (chatRoom.getType() == ChatRoomType.OPEN_PRIVATE) {
            if (password == null || password.isBlank()) {
                throw new BusinessException(ErrorCode.CHATROOM_PASSWORD_REQUIRED);
            }
            if (!chatRoom.matchesPassword(password, passwordEncoder)) {
                throw new BusinessException(ErrorCode.INVALID_CHATROOM_PASSWORD);
            }
        }

        // 4. 기존 멤버십 조회 (isActive 관계없이)
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

        // 5. userId로 nickname 조회 후 메시지 생성
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        return ChatRoomJoinResponse.of(roomId, ChatRoomRole.MEMBER.name(), user.getNickname());
    }

}
