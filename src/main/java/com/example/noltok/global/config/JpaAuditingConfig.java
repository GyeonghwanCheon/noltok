package com.example.noltok.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

// @EnableJpaAuditing을 NoltokApplication(메인 클래스)이 아닌 별도 설정
// 클래스로 분리 — @WebMvcTest는 슬라이스 테스트라도 @SpringBootConfiguration으로
// NoltokApplication을 설정 소스로 잡는데, 메인 클래스에 @EnableJpaAuditing이
// 있으면 EntityManagerFactory가 없는 슬라이스에서도 JPA 감사 인프라를
// 초기화하려다 "JPA metamodel must not be empty" 에러로 기동 자체가 실패함
// (docs/troubleshooting-log.md 참고). 별도 @Configuration으로 빼면 전체
// 앱(@ComponentScan 대상)에서는 그대로 동작하고, @WebMvcTest처럼 제한된
// 스캔 범위에서는 자연스럽게 제외됨
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
