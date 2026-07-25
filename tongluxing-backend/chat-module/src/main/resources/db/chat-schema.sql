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
  cleared_before_message_id BIGINT NULL,
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

CREATE TABLE IF NOT EXISTS chat_group_item (
  id BIGINT NOT NULL,
  conversation_id BIGINT NOT NULL,
  item_type VARCHAR(24) NOT NULL COMMENT 'ANNOUNCEMENT/RESOURCE/POLL/REMINDER',
  title VARCHAR(120) NOT NULL,
  content VARCHAR(2000) NULL,
  payload_json JSON NULL,
  item_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  creator_user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_chat_group_item (conversation_id,item_type,item_status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群公告、资料、投票与提醒';

CREATE TABLE IF NOT EXISTS chat_group_vote (
  id BIGINT NOT NULL,
  item_id BIGINT NOT NULL,
  option_key VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_poll_user (item_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群投票记录';

CREATE TABLE IF NOT EXISTS chat_reminder_dispatch (
  item_id BIGINT NOT NULL,
  dispatched_at DATETIME NOT NULL,
  PRIMARY KEY (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程提醒去重发送记录';

CREATE TABLE IF NOT EXISTS trip_confirmation (
  id BIGINT NOT NULL,
  trip_id BIGINT NOT NULL,
  conversation_id BIGINT NOT NULL,
  creator_user_id BIGINT NOT NULL,
  confirmation_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME NOT NULL,
  closed_at DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_trip_confirmation (trip_id,confirmation_status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程开始前成员确认批次';

CREATE TABLE IF NOT EXISTS trip_confirm_record (
  id BIGINT NOT NULL,
  confirmation_id BIGINT NOT NULL,
  trip_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
  reject_reason VARCHAR(300) NULL,
  confirm_time DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_trip_confirm_member (confirmation_id,user_id),
  KEY idx_trip_confirm_trip (trip_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程成员参加确认记录';

CREATE TABLE IF NOT EXISTS chat_member_location (
  id BIGINT NOT NULL,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  latitude DECIMAL(10,7) NOT NULL,
  longitude DECIMAL(10,7) NOT NULL,
  speed DECIMAL(8,2) NULL,
  sharing_flag TINYINT(1) NOT NULL DEFAULT 1,
  recorded_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_member_location (conversation_id,user_id),
  KEY idx_chat_location_time (conversation_id,recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程群成员最新共享位置';

CREATE TABLE IF NOT EXISTS chat_report (
  id BIGINT NOT NULL,
  conversation_id BIGINT NOT NULL,
  reporter_user_id BIGINT NOT NULL,
  target_type VARCHAR(20) NOT NULL COMMENT 'CONVERSATION/MEMBER/MESSAGE',
  target_id VARCHAR(64) NOT NULL,
  report_type VARCHAR(32) NOT NULL,
  reason VARCHAR(500) NOT NULL,
  evidence_json JSON NULL,
  report_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewer_id BIGINT NULL,
  review_note VARCHAR(500) NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  PRIMARY KEY (id),
  KEY idx_chat_report_review (report_status,created_at),
  KEY idx_chat_report_conversation (conversation_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊、成员与消息举报';
