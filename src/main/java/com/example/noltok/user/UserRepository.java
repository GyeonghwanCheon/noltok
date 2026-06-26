package com.example.noltok.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // existsByEmail → existsByEmailAndIsActive로 교체
    // 이유: 탈퇴 유저(isActive=false)는 중복 이메일로 처리하지 않음
    //       탈퇴 후 동일 이메일 재가입 허용
    boolean existsByEmailAndIsActive(String email, boolean isActive);

    Optional<User> findByEmail(String email);

    // 닉네임 중복 체크
    // existsByNickname(nickname) 만으로는 부족한 이유:
    // → 본인이 현재 닉네임과 동일한 닉네임으로 수정 요청 시
    //   자기 자신의 닉네임도 중복으로 잡힘
    // → existsByNicknameAndIdNot(nickname, userId) 사용
    //   "해당 nickname을 가진 유저 중 내(userId)가 아닌 사람이 있는가"
    boolean existsByNicknameAndIdNot(String nickname, Long id);

    // @Query 사용 이유:
    // → Query Method로 표현하면
    //   findByNicknameContainingAndIdNot() 처럼 메서드명이 너무 길어짐
    // → @Query로 의도를 명확하게 표현
    // → LIKE %:nickname% = 닉네임 부분일치 검색
    // → u.id != :userId = 본인 제외
    @Query("SELECT u FROM User u WHERE u.nickname LIKE %:nickname% AND u.id != :userId")
    List<User> searchByNicknameExcludingMe(
            @Param("nickname") String nickname,
            @Param("userId") Long userId
    );

    Optional<User> findByNickname(String nickname);
    // 이유: 채팅방 생성/초대 시 닉네임으로 유저를 찾아야 함
    //       existsByNicknameAndIdNot()과 별개로 User 객체 자체가 필요
}
