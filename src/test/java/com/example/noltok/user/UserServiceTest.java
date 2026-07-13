package com.example.noltok.user;

import com.example.noltok.auth.RefreshTokenRepository;
import com.example.noltok.global.exception.BusinessException;
import com.example.noltok.global.exception.ErrorCode;
import com.example.noltok.user.dto.request.ChangePasswordRequest;
import com.example.noltok.user.dto.request.UpdateProfileRequest;
import com.example.noltok.user.dto.response.DeleteAccountResponse;
import com.example.noltok.user.dto.response.UserResponse;
import com.example.noltok.user.dto.response.UserSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// DB 없이 순수 JVM에서 도는 단위 테스트 — Repository는 전부 Mock
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private UserService userService;

    private final Long userId = 1L;

    private User testUser(Long id, String nickname, String encodedPassword) {
        User user = User.create(nickname + "@test.com", encodedPassword, nickname);
        ReflectionTestUtils.setField(user, "id", id);
        ReflectionTestUtils.setField(user, "createdAt", LocalDateTime.now());
        return user;
    }

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder, refreshTokenRepository);
    }

    // ── getMyInfo() ────────────────────────────────────────────

    @Test
    void getMyInfo_정상_조회() {
        // given
        User user = testUser(userId, "나", "encoded-pw");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        UserResponse response = userService.getMyInfo(userId);

        // then
        assertThat(response.nickname()).isEqualTo("나");
    }

    @Test
    void getMyInfo_존재하지_않으면_예외() {
        // given
        given(userRepository.findById(userId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getMyInfo(userId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── updateMyInfo() ─────────────────────────────────────────

    @Test
    void updateMyInfo_닉네임_변경시_중복이_없으면_정상_반영된다() {
        // given
        User user = testUser(userId, "이전닉네임", "encoded-pw");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.existsByNicknameAndIdNot("새닉네임", userId)).willReturn(false);

        // when
        UserResponse response = userService.updateMyInfo(userId, new UpdateProfileRequest("새닉네임", null));

        // then
        assertThat(response.nickname()).isEqualTo("새닉네임");
    }

    @Test
    void updateMyInfo_닉네임이_중복이면_예외() {
        // given
        User user = testUser(userId, "이전닉네임", "encoded-pw");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(userRepository.existsByNicknameAndIdNot("중복닉네임", userId)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.updateMyInfo(userId, new UpdateProfileRequest("중복닉네임", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATE_NICKNAME);
    }

    @Test
    void updateMyInfo_닉네임을_안_보내면_중복체크를_하지_않는다() {
        // given: profileImageUrl만 수정하는 케이스
        User user = testUser(userId, "기존닉네임", "encoded-pw");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        userService.updateMyInfo(userId, new UpdateProfileRequest(null, "http://image.url"));

        // then
        verify(userRepository, never()).existsByNicknameAndIdNot(anyString(), org.mockito.ArgumentMatchers.anyLong());
    }

    // ── changePassword() ───────────────────────────────────────

    @Test
    void changePassword_정상_변경() {
        // given
        User user = testUser(userId, "유저", "encoded-old");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("oldPw1234", "encoded-old")).willReturn(true);
        given(passwordEncoder.matches("newPw1234", "encoded-old")).willReturn(false);
        given(passwordEncoder.encode("newPw1234")).willReturn("encoded-new");

        // when
        userService.changePassword(userId, new ChangePasswordRequest("oldPw1234", "newPw1234", "newPw1234"));

        // then
        assertThat(user.getPassword()).isEqualTo("encoded-new");
    }

    @Test
    void changePassword_현재_비밀번호가_틀리면_예외() {
        // given
        User user = testUser(userId, "유저", "encoded-old");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("wrongPw1234", "encoded-old")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(userId,
                new ChangePasswordRequest("wrongPw1234", "newPw1234", "newPw1234")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_CREDENTIALS);
    }

    @Test
    void changePassword_새_비밀번호가_현재와_같으면_예외() {
        // given
        User user = testUser(userId, "유저", "encoded-old");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        // currentPassword와 newPassword가 둘 다 "oldPw1234"라 같은 스텁이 두 호출 모두 커버함
        given(passwordEncoder.matches("oldPw1234", "encoded-old")).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(userId,
                new ChangePasswordRequest("oldPw1234", "oldPw1234", "oldPw1234")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SAME_AS_CURRENT_PASSWORD);
    }

    @Test
    void changePassword_새_비밀번호와_확인이_다르면_예외() {
        // given
        User user = testUser(userId, "유저", "encoded-old");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));
        given(passwordEncoder.matches("oldPw1234", "encoded-old")).willReturn(true);
        given(passwordEncoder.matches("newPw1234", "encoded-old")).willReturn(false);

        // when & then
        assertThatThrownBy(() -> userService.changePassword(userId,
                new ChangePasswordRequest("oldPw1234", "newPw1234", "differentPw1234")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_NOT_MATCHED);
    }

    // ── searchUsers() ──────────────────────────────────────────

    @Test
    void searchUsers_닉네임_부분일치로_검색한다() {
        // given
        User found = testUser(2L, "검색결과닉네임", "encoded-pw");
        given(userRepository.searchByNicknameExcludingMe("닉네임", userId)).willReturn(List.of(found));

        // when
        List<UserSummaryResponse> responses = userService.searchUsers("닉네임", userId);

        // then
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).nickname()).isEqualTo("검색결과닉네임");
    }

    // ── getUserDetail() ────────────────────────────────────────

    @Test
    void getUserDetail_정상_조회() {
        // given
        User target = testUser(2L, "상대방", "encoded-pw");
        given(userRepository.findById(2L)).willReturn(Optional.of(target));

        // when
        UserSummaryResponse response = userService.getUserDetail(2L);

        // then
        assertThat(response.nickname()).isEqualTo("상대방");
    }

    @Test
    void getUserDetail_존재하지_않으면_예외() {
        // given
        given(userRepository.findById(2L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.getUserDetail(2L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.USER_NOT_FOUND);
    }

    // ── deleteMyAccount() ──────────────────────────────────────

    @Test
    void deleteMyAccount_Soft_Delete로_처리하고_RefreshToken도_삭제한다() {
        // given
        User user = testUser(userId, "탈퇴할유저", "encoded-pw");
        given(userRepository.findById(userId)).willReturn(Optional.of(user));

        // when
        DeleteAccountResponse response = userService.deleteMyAccount(userId);

        // then
        assertThat(response.userId()).isEqualTo(userId);
        assertThat(user.isActive()).isFalse();
        verify(refreshTokenRepository).deleteByUserId(userId);
    }
}
