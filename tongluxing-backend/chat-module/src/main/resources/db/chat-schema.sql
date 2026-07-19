CREATE TABLE IF NOT EXISTS chat_conversation (
  id BIGINT NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  biz_id BIGINT NOT NULL,
  conversation_name VARCHAR(64) NOT NULL,
  conversation_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  provider_type VARCHAR(32) NOT NULL DEFAULT 'MOCK',
  provider_conversation_key VARCHAR(128) NULL,
  last_message_id BIGINT NULL,
  last_message_preview VARCHAR(120) NULL,
  last_message_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_biz (biz_type, biz_id, deleted),
  KEY idx_chat_last_time (conversation_status, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_conversation_member (
  id BIGINT NOT NULL,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  member_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
  member_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  unread_count INT NOT NULL DEFAULT 0,
  muted_flag TINYINT(1) NOT NULL DEFAULT 0,
  pinned_flag TINYINT(1) NOT NULL DEFAULT 0,
  last_read_message_id BIGINT NULL,
  joined_at DATETIME NOT NULL,
  exited_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_member (conversation_id, user_id, deleted),
  KEY idx_chat_member_user (user_id, member_status),
  KEY idx_chat_member_conversation (conversation_id, member_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_join_application (
  id BIGINT NOT NULL,
  conversation_id BIGINT NOT NULL,
  applicant_user_id BIGINT NOT NULL,
  application_message VARCHAR(120) NULL,
  application_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewer_user_id BIGINT NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_chat_join_owner_queue (conversation_id, application_status, created_at),
  KEY idx_chat_join_applicant (applicant_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SET @chat_add_muted = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='chat_conversation_member' AND column_name='muted_flag')=0,
  'ALTER TABLE chat_conversation_member ADD COLUMN muted_flag TINYINT(1) NOT NULL DEFAULT 0 AFTER unread_count',
  'SELECT 1');
PREPARE chat_stmt FROM @chat_add_muted;
EXECUTE chat_stmt;
DEALLOCATE PREPARE chat_stmt;

SET @chat_add_pinned = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='chat_conversation_member' AND column_name='pinned_flag')=0,
  'ALTER TABLE chat_conversation_member ADD COLUMN pinned_flag TINYINT(1) NOT NULL DEFAULT 0 AFTER muted_flag',
  'SELECT 1');
PREPARE chat_stmt FROM @chat_add_pinned;
EXECUTE chat_stmt;
DEALLOCATE PREPARE chat_stmt;

CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT NOT NULL,
  conversation_id BIGINT NOT NULL,
  sender_user_id BIGINT NULL,
  message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
  message_payload_json JSON NOT NULL,
  message_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
  provider_message_key VARCHAR(128) NULL,
  sent_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_chat_msg_conversation_time (conversation_id, sent_at),
  KEY idx_chat_msg_sender_time (sender_user_id, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS message_risk (
  id BIGINT NOT NULL,
  message_id BIGINT NOT NULL,
  risk_level VARCHAR(16) NOT NULL,
  risk_type VARCHAR(20) NOT NULL,
  confidence INT NOT NULL DEFAULT 0,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  matched_rule VARCHAR(80) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_risk_message (message_id),
  KEY idx_message_risk_review (status, risk_level, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
