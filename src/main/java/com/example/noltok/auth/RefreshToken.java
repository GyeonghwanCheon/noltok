package com.example.noltok.auth;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // User와 1:1 관계지만 @OneToOne 대신 userId만 저장
    // 이유: 연관관계 매핑 시 User 조회가 강제됨
    //       토큰 검증 시 User 정보가 필요 없으므로 불필요한 조회를 막음
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token", nullable = false, length = 500)
    private String token;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private RefreshToken(Long userId, String token, LocalDateTime expiresAt) {
        this.userId = userId;
        this.token = token;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public static RefreshToken create(Long userId, String token, long refreshExpiration) {
        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(refreshExpiration / 1000);  // ms → seconds 변환
        return new RefreshToken(userId, token, expiresAt);
    }

    // 토큰 갱신 (Refresh Token Rotation 전략)
    // 이유: Refresh Token을 재사용하지 않고 재발급마다 새 토큰으로 교체
    //       탈취된 토큰이 재사용되면 탐지 가능
    public void rotate(String newToken, long refreshExpiration) {
        this.token = newToken;
        this.expiresAt = LocalDateTime.now()
                .plusSeconds(refreshExpiration / 1000);
    }
}
