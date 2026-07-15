package com.example.noltok.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // 탈퇴 유저(isActive=false)는 중복 이메일 처리에서 제외 (재가입 허용)
    boolean existsByEmailAndIsActive(String email, boolean isActive);

    Optional<User> findByEmail(String email);

    // 본인의 현재 닉네임은 중복 체크에서 제외
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    // 닉네임 부분일치 검색, 본인 제외
    @Query("SELECT u FROM User u WHERE u.nickname LIKE %:nickname% AND u.id != :userId")
    List<User> searchByNicknameExcludingMe(
            @Param("nickname") String nickname,
            @Param("userId") Long userId
    );

    // 채팅방 생성/초대 시 닉네임으로 유저 조회
    Optional<User> findByNickname(String nickname);
}
