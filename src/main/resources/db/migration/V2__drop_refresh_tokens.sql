-- Refresh Token이 Redis로 이전된 뒤(Phase 7, 2026-07-09) 안 지워진
-- 유령 테이블 정리. RefreshToken 엔티티는 이제 @RedisHash라 이 테이블을
-- 참조하는 JPA 매핑이 전혀 없음 (docs/decision-log.md 2026-07-09 참고).
DROP TABLE IF EXISTS `refresh_tokens`;
