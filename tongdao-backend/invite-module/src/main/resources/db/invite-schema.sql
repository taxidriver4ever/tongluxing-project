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
  invite_code VARCHAR(16) NULL, relation_status VARCHAR(16) NOT NULL DEFAULT 'BOUND',
  bind_source VARCHAR(32) NOT NULL DEFAULT 'LINK',
  bind_source_value VARCHAR(64) NULL,
  invitee_registered_at DATETIME NULL,
  bound_at DATETIME NOT NULL, first_team_completed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
  UNIQUE KEY uk_invite_relation_invitee (invitee_user_id, deleted),
  KEY idx_invite_relation_inviter (inviter_user_id, relation_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS invite_reward_record (
  id BIGINT NOT NULL, relation_id BIGINT NOT NULL, beneficiary_user_id BIGINT NOT NULL,
  rule_code VARCHAR(32) NOT NULL, reward_biz_no VARCHAR(64) NOT NULL,
  reward_snapshot_json JSON NOT NULL, reward_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  failure_reason VARCHAR(255) NULL, granted_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
  UNIQUE KEY uk_invite_reward_biz (reward_biz_no, deleted),
  KEY idx_invite_reward_user (beneficiary_user_id, reward_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
