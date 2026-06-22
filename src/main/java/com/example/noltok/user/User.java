package com.example.noltok.user;

import com.example.noltok.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "nickname", nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl;

    private User(String email, String password, String nickname) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
    }

    public static User create(String email, String encodedPassword, String nickname) {
        return new User(email, encodedPassword, nickname);
    }

    // updateProfile 설계 의도:
    // → null로 들어온 필드는 기존 값 유지 (선택적 수정)
    // → "홍길동"이 nickname을 null로 보내면 기존 "홍길동" 그대로 유지
    // → profileImageUrl도 동일한 방식
    public void updateProfile(String nickname, String profileImageUrl) {
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (profileImageUrl != null) {
            this.profileImageUrl = profileImageUrl;
        }
    }

    // changePassword 설계 의도:
    // → 암호화는 Service에서 처리 후 encodedPassword를 받음
    // → Entity는 "저장"만 담당, 암호화 책임은 Service에
    // → 평문 비밀번호가 Entity 안으로 들어오지 않도록 설계
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
}