package com.example.noltok.user;

import com.example.noltok.auth.RefreshTokenRepository;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.dto.request.ChangePasswordRequest;
import com.example.noltok.user.dto.request.UpdateProfileRequest;
import com.example.noltok.user.dto.response.DeleteAccountResponse;
import com.example.noltok.user.dto.response.UserResponse;
import com.example.noltok.user.dto.response.UserSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;


    // 내 정보 조회
    @Transactional(readOnly = true)
    public UserResponse getMyInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }


    @Transactional
    public UserResponse updateMyInfo(Long userId, UpdateProfileRequest request) {

        // 1. userId로 User 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 닉네임 변경 요청이 있을 때만 중복 체크
        if (request.nickname() != null) {
            validateDuplicateNickname(request.nickname(), userId);
        }

        // 3. Entity 변경 (JPA 변경감지로 자동 UPDATE, save() 호출 불필요)
        user.updateProfile(request.nickname(), request.profileImageUrl());

        return UserResponse.from(user);
    }


    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {

        // 1. userId로 User 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 현재 비밀번호 검증 (틀린 이유 노출 방지 위해 로그인과 동일 에러코드 사용)
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. 새 비밀번호가 현재 비밀번호와 같은지 확인 (BCrypt는 매번 해시가 달라 matches()로 비교)
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        // 4. 새 비밀번호와 확인 비밀번호 일치 여부 확인
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_NOT_MATCHED);
        }

        // 5. 새 비밀번호 암호화 후 변경
        String encodedPassword = passwordEncoder.encode(request.newPassword());
        user.changePassword(encodedPassword);
    }

    // 유저 검색 (결과 없으면 빈 리스트 반환 — 404 아님, 정상 결과)
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> searchUsers(String nickname, Long userId) {
        return userRepository.searchByNicknameExcludingMe(nickname, userId)
                .stream()
                .map(UserSummaryResponse::from)
                .toList();
    }

    // 유저 상세 조회
    @Transactional(readOnly = true)
    public UserSummaryResponse getUserDetail(Long targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserSummaryResponse.from(user);
    }

    // 본인의 현재 닉네임은 중복에서 제외
    private void validateDuplicateNickname(String nickname, Long userId) {
        if (userRepository.existsByNicknameAndIdNot(nickname, userId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }


    @Transactional
    public DeleteAccountResponse deleteMyAccount(Long userId) {
        // 1. userId로 User 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. Soft Delete 처리
        user.deactivate();

        // 3. Refresh Token 삭제 (탈퇴 후 재발급 차단)
        refreshTokenRepository.deleteByUserId(userId);

        return new DeleteAccountResponse(userId);
    }

}
