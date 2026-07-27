CREATE TABLE IF NOT EXISTS invite_code (
  id BIGINT NOT NULL, user_id BIGINT NOT NULL, invite_code VARCHAR(16) NOT NULL,
  enabled_flag TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
  UNIQUE KEY uk_invite_code_user (user_id, deleted),
  UNIQUE KEY uk_invite_code_value (invite_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS invite_relation (
  id BIGINT NOT NULL, inviter_user_id BIGINT NOT NULL, invitee_user_id BIGINT NOT NULL,
  invite_code VARCHAR(16) NULL, relation_status VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',
  bind_source VARCHAR(32) NOT NULL DEFAULT 'MANUAL_CODE',
  bind_source_value VARCHAR(64) NULL,
  request_id VARCHAR(64) NULL,
  invitee_registered_at DATETIME NULL,
  bound_at DATETIME NOT NULL, first_team_completed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
  UNIQUE KEY uk_invite_relation_invitee (invitee_user_id, deleted),
  UNIQUE KEY uk_invite_relation_request (request_id, deleted),
  KEY idx_invite_relation_inviter (inviter_user_id, relation_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS invite_reward_record (
  id BIGINT NOT NULL, relation_id BIGINT NOT NULL, beneficiary_user_id BIGINT NOT NULL,
  inviter_user_id BIGINT NOT NULL, invitee_user_id BIGINT NOT NULL,
  rule_code VARCHAR(64) NOT NULL, reward_rule_code VARCHAR(64) NOT NULL,
  reward_type VARCHAR(32) NOT NULL DEFAULT 'GROWTH_VALUE', reward_value INT NOT NULL DEFAULT 0,
  reward_biz_no VARCHAR(64) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
  reward_snapshot_json JSON NOT NULL, reward_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  triggered_at DATETIME NOT NULL, failure_reason VARCHAR(255) NULL,
  granted_at DATETIME NULL, issued_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
  UNIQUE KEY uk_invite_reward_biz (reward_biz_no, deleted),
  UNIQUE KEY uk_invite_reward_idempotency (idempotency_key, deleted),
  UNIQUE KEY uk_invite_reward_relation_rule (relation_id, rule_code, deleted),
  KEY idx_invite_reward_user (beneficiary_user_id, reward_status, created_at),
  KEY idx_invite_reward_invitee (invitee_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS invite_reward_rule (
  id BIGINT NOT NULL,
  rule_code VARCHAR(64) NOT NULL,
  reward_type VARCHAR(32) NOT NULL DEFAULT 'GROWTH_VALUE',
  reward_value INT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_invite_reward_rule_code (rule_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


INSERT IGNORE INTO invite_reward_rule
(id, rule_code, reward_type, reward_value, status, created_at, updated_at, deleted)
VALUES
(202607270001, 'INVITE_REGISTER_SUCCESS', 'GROWTH_VALUE', 50, 'ENABLED', NOW(), NOW(), 0),
(202607270002, 'INVITEE_FIRST_TEAM_COMPLETED', 'GROWTH_VALUE', 100, 'ENABLED', NOW(), NOW(), 0),
(202607270003, 'INVITE_STAGE_3', 'GROWTH_VALUE', 200, 'ENABLED', NOW(), NOW(), 0),
(202607270004, 'INVITE_STAGE_10', 'GROWTH_VALUE', 500, 'ENABLED', NOW(), NOW(), 0),
(202607270005, 'INVITE_STAGE_30', 'GROWTH_VALUE', 2000, 'ENABLED', NOW(), NOW(), 0),
(202607270006, 'INVITE_STAGE_50', 'GROWTH_VALUE', 5000, 'ENABLED', NOW(), NOW(), 0);
