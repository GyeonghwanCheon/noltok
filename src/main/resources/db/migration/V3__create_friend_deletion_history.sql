-- Friend는 Hard Delete라 삭제되면 이력이 안 남음(decision-log.md 2026-07-02) —
-- friend.deleted Kafka 이벤트를 소비해서 스냅샷을 남기는 이력 테이블 (optimization-log.md [9])
CREATE TABLE `friend_deletion_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `friend_id` bigint NOT NULL,
  `requester_id` bigint NOT NULL,
  `receiver_id` bigint NOT NULL,
  `deleted_by` bigint NOT NULL,
  `friend_since` datetime(6) NOT NULL,
  `deleted_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
