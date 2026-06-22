package com.example.noltok.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    // 닉네임 중복 체크
    // existsByNickname(nickname) 만으로는 부족한 이유:
    // → 본인이 현재 닉네임과 동일한 닉네임으로 수정 요청 시
    //   자기 자신의 닉네임도 중복으로 잡힘
    // → existsByNicknameAndIdNot(nickname, userId) 사용
    //   "해당 nickname을 가진 유저 중 내(userId)가 아닌 사람이 있는가"
    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
