package com.example.noltok.user;

import com.example.noltok.auth.RefreshTokenRepository;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.dto.*;
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


    // 내 정보 조회 (읽기 전용 → readOnly = true)
    // 이유: readOnly = true는 JPA가 스냅샷 저장, 변경감지를 생략
    //       불필요한 DB 부하를 줄여주는 성능 최적화
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
        // → null이면 닉네임 변경 요청이 없는 것이므로 체크 불필요
        if (request.nickname() != null) {
            validateDuplicateNickname(request.nickname(), userId);
        }

        // 3. Entity 변경 (변경감지로 자동 UPDATE 쿼리 실행)
        // → @Transactional이 있으므로 save() 호출 없이도 DB에 반영됨
        // → JPA 변경감지(dirty checking): 트랜잭션 종료 시 변경된 필드 자동 UPDATE
        user.updateProfile(request.nickname(), request.profileImageUrl());

        return UserResponse.from(user);
    }


    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {

        // 1. userId로 User 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 2. 현재 비밀번호 검증
        // → 틀리면 INVALID_CREDENTIALS (401)
        // → 로그인과 동일한 에러코드 사용 이유:
        //   "현재 비밀번호가 틀렸습니다"를 구체적으로 알려주면
        //   공격자가 비밀번호 브루트포싱에 활용 가능
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 3. 현재 비밀번호와 새 비밀번호 동일 여부 확인
        // → BCrypt 특성상 같은 평문이라도 매번 다른 해시값 생성
        // → 따라서 해시값 비교가 아닌 matches()로 비교
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

    // 유저 검색
    // readOnly = true 이유: 조회만 하는 메서드, 불필요한 변경감지 생략
    @Transactional(readOnly = true)
    public List<UserSummaryResponse> searchUsers(String nickname, Long userId) {
        return userRepository.searchByNicknameExcludingMe(nickname, userId)
                .stream()
                .map(UserSummaryResponse::from)
                .toList();
        // 검색 결과가 없으면 빈 리스트 반환
        // → 404로 처리하지 않는 이유:
        //   "해당 닉네임의 유저가 없음"은 정상적인 결과
        //   에러가 아니라 빈 배열로 응답하는 게 RESTful 관례
    }

    // 유저 상세 조회
    @Transactional(readOnly = true)
    public UserSummaryResponse getUserDetail(Long targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserSummaryResponse.from(user);
    }

    // 본인 제외 닉네임 중복 체크
    // existsByNicknameAndIdNot 이유:
    // → 본인이 동일한 닉네임으로 수정 요청 시 중복 에러 방지
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
        // → DB에서 실제 삭제하지 않고 isActive = false로 변경
        user.deactivate();

        // 3. Refresh Token 삭제
        // → 탈퇴 후 토큰 재발급 차단
        // → 로그아웃과 동일한 처리
        refreshTokenRepository.deleteByUserId(userId);

        return new DeleteAccountResponse(userId);
    }

}
