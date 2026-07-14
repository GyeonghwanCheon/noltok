-- 현재 실제 DB(ddl-auto=update로 쌓인 스키마)를 그대로 스냅샷.
-- refresh_tokens는 Phase 7(2026-07-09)에 Redis로 이전된 뒤 안 지워진
-- 유령 테이블이라 여기 포함하지 않고 V2에서 별도로 정리한다.

CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `email` varchar(100) NOT NULL,
  `password` varchar(255) NOT NULL,
  `nickname` varchar(20) NOT NULL,
  `profile_image_url` varchar(500) DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK2ty1xmrrgtn89xt7kyxx6ta7h` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_rooms` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `roomname` varchar(100) DEFAULT NULL,
  `type` enum('DIRECT','GROUP','OPEN','OPEN_PRIVATE') NOT NULL,
  `password` varchar(100) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_room_members` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `room_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `role` enum('ADMIN','MEMBER') NOT NULL,
  `last_read_message_id` bigint DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `joined_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKeatdjwpl171huu28pvsan941c` (`room_id`,`user_id`),
  KEY `idx_chat_room_members_user_id_is_active` (`user_id`,`is_active`),
  CONSTRAINT `FKdvub8k7sypahkamqjaiokb44t` FOREIGN KEY (`room_id`) REFERENCES `chat_rooms` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `chat_messages` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `room_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `content` text,
  `type` enum('FILE','IMAGE','TEXT') NOT NULL,
  `file_url` varchar(255) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_chat_messages_room_id_id` (`room_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `friends` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `requester_id` bigint NOT NULL,
  `receiver_id` bigint NOT NULL,
  `status` enum('ACCEPTED','PENDING','REJECTED') NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_friends_requester_receiver` (`requester_id`,`receiver_id`),
  KEY `idx_friends_receiver_requester` (`receiver_id`,`requester_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `blocks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `blocker_id` bigint NOT NULL,
  `blocked_id` bigint NOT NULL,
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_blocks_blocker_blocked` (`blocker_id`,`blocked_id`),
  KEY `idx_blocks_blocked_blocker` (`blocked_id`,`blocker_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `receiver_id` bigint NOT NULL,
  `type` enum('CHAT_MESSAGE','FRIEND_REQUEST','ROOM_INVITE') NOT NULL,
  `content` varchar(255) NOT NULL,
  `is_read` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notifications_receiver_id_id` (`receiver_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
