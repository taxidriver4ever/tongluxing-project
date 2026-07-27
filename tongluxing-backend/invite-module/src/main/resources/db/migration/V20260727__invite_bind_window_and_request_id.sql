-- 同路行 MVP v3 邀请绑定与奖励字段迁移（MySQL 8，幂等）。

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_relation' AND column_name='request_id')=0,
  'ALTER TABLE invite_relation ADD COLUMN request_id VARCHAR(64) NULL AFTER bind_source_value', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='invite_relation' AND index_name='uk_invite_relation_request')=0,
  'CREATE UNIQUE INDEX uk_invite_relation_request ON invite_relation(request_id, deleted)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS invite_reward_rule (
  id BIGINT NOT NULL PRIMARY KEY, rule_code VARCHAR(64) NOT NULL,
  reward_type VARCHAR(32) NOT NULL DEFAULT 'GROWTH_VALUE', reward_value INT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_invite_reward_rule_code (rule_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO invite_reward_rule
(id, rule_code, reward_type, reward_value, status, created_at, updated_at, deleted) VALUES
(202607270001, 'INVITE_REGISTER_SUCCESS', 'GROWTH_VALUE', 50, 'ENABLED', NOW(), NOW(), 0),
(202607270002, 'INVITEE_FIRST_TEAM_COMPLETED', 'GROWTH_VALUE', 100, 'ENABLED', NOW(), NOW(), 0),
(202607270003, 'INVITE_STAGE_3', 'GROWTH_VALUE', 200, 'ENABLED', NOW(), NOW(), 0),
(202607270004, 'INVITE_STAGE_10', 'GROWTH_VALUE', 500, 'ENABLED', NOW(), NOW(), 0),
(202607270005, 'INVITE_STAGE_30', 'GROWTH_VALUE', 2000, 'ENABLED', NOW(), NOW(), 0),
(202607270006, 'INVITE_STAGE_50', 'GROWTH_VALUE', 5000, 'ENABLED', NOW(), NOW(), 0);

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='inviter_user_id')=0, 'ALTER TABLE invite_reward_record ADD COLUMN inviter_user_id BIGINT NULL AFTER beneficiary_user_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='invitee_user_id')=0, 'ALTER TABLE invite_reward_record ADD COLUMN invitee_user_id BIGINT NULL AFTER inviter_user_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='reward_rule_code')=0, 'ALTER TABLE invite_reward_record ADD COLUMN reward_rule_code VARCHAR(64) NULL AFTER rule_code', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='reward_type')=0, 'ALTER TABLE invite_reward_record ADD COLUMN reward_type VARCHAR(32) NOT NULL DEFAULT ''GROWTH_VALUE'' AFTER reward_rule_code', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='reward_value')=0, 'ALTER TABLE invite_reward_record ADD COLUMN reward_value INT NOT NULL DEFAULT 0 AFTER reward_type', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='idempotency_key')=0, 'ALTER TABLE invite_reward_record ADD COLUMN idempotency_key VARCHAR(128) NULL AFTER reward_biz_no', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='triggered_at')=0, 'ALTER TABLE invite_reward_record ADD COLUMN triggered_at DATETIME NULL AFTER reward_status', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='issued_at')=0, 'ALTER TABLE invite_reward_record ADD COLUMN issued_at DATETIME NULL AFTER granted_at', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE invite_reward_record r
JOIN invite_relation rel ON rel.id=r.relation_id
LEFT JOIN invite_reward_rule rr ON rr.rule_code=r.rule_code AND rr.deleted=0
SET r.inviter_user_id=COALESCE(r.inviter_user_id, rel.inviter_user_id),
    r.invitee_user_id=COALESCE(r.invitee_user_id, rel.invitee_user_id),
    r.reward_rule_code=COALESCE(r.reward_rule_code, r.rule_code),
    r.reward_type=COALESCE(r.reward_type, rr.reward_type, 'GROWTH_VALUE'),
    r.reward_value=CASE WHEN r.reward_value=0 THEN COALESCE(rr.reward_value,0) ELSE r.reward_value END,
    r.idempotency_key=COALESCE(r.idempotency_key, r.reward_biz_no),
    r.triggered_at=COALESCE(r.triggered_at, r.created_at),
    r.issued_at=COALESCE(r.issued_at, r.granted_at)
WHERE r.deleted=0;

UPDATE invite_reward_record SET reward_status='ISSUED' WHERE reward_status='GRANTED' AND deleted=0;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND index_name='uk_invite_reward_idempotency')=0,
  'CREATE UNIQUE INDEX uk_invite_reward_idempotency ON invite_reward_record(idempotency_key, deleted)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
