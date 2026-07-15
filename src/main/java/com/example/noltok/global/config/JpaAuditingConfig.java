package com.example.noltok.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// 메인 클래스가 아닌 별도 설정으로 분리 — @WebMvcTest가 메인 클래스를 설정
// 소스로 잡을 때 이 설정까지 로드해서 기동 실패하는 걸 방지
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
