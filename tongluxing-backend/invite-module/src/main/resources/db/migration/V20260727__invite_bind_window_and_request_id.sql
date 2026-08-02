-- 同路行 MVP v3 邀请绑定与奖励字段迁移（MySQL 8，幂等）。

-- 为邀请关系增加客户端请求幂等号；先查 information_schema，已存在时执行空操作。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_relation' AND column_name='request_id')=0,
  'ALTER TABLE invite_relation ADD COLUMN request_id VARCHAR(64) NULL COMMENT ''请求唯一标识，用于链路追踪或幂等控制'' AFTER bind_source_value', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- request_id 与逻辑删除标记组成唯一索引，支持已删除记录后重新使用请求号。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='invite_relation' AND index_name='uk_invite_relation_request')=0,
  'CREATE UNIQUE INDEX uk_invite_relation_request ON invite_relation(request_id, deleted)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 新增可运营的奖励规则表，代码默认值只作为缺省保底。
CREATE TABLE IF NOT EXISTS invite_reward_rule (
  id BIGINT NOT NULL PRIMARY KEY COMMENT '邀请奖励规则主键',
  rule_code VARCHAR(64) NOT NULL COMMENT '邀请奖励规则编码',
  reward_type VARCHAR(32) NOT NULL DEFAULT 'GROWTH_VALUE' COMMENT '奖励类型，例如成长值或优惠券',
  reward_value INT NOT NULL COMMENT '奖励数值',
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '奖励规则状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '规则创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '规则最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除、1已删除',
  UNIQUE KEY uk_invite_reward_rule_code (rule_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邀请奖励规则表';

-- 幂等写入注册、首次组队和四个里程碑初始规则。
INSERT IGNORE INTO invite_reward_rule
(id, rule_code, reward_type, reward_value, status, created_at, updated_at, deleted) VALUES
(202607270001, 'INVITE_REGISTER_SUCCESS', 'GROWTH_VALUE', 50, 'ENABLED', NOW(), NOW(), 0),
(202607270002, 'INVITEE_FIRST_TEAM_COMPLETED', 'GROWTH_VALUE', 100, 'ENABLED', NOW(), NOW(), 0),
(202607270003, 'INVITE_STAGE_3', 'GROWTH_VALUE', 200, 'ENABLED', NOW(), NOW(), 0),
(202607270004, 'INVITE_STAGE_10', 'GROWTH_VALUE', 500, 'ENABLED', NOW(), NOW(), 0),
(202607270005, 'INVITE_STAGE_30', 'GROWTH_VALUE', 2000, 'ENABLED', NOW(), NOW(), 0),
(202607270006, 'INVITE_STAGE_50', 'GROWTH_VALUE', 5000, 'ENABLED', NOW(), NOW(), 0);

-- 扩展历史奖励台账：补齐关系双方、规则快照、幂等键及触发/发放时间。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='inviter_user_id')=0, 'ALTER TABLE invite_reward_record ADD COLUMN inviter_user_id BIGINT NULL COMMENT ''邀请人用户ID'' AFTER beneficiary_user_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='invitee_user_id')=0, 'ALTER TABLE invite_reward_record ADD COLUMN invitee_user_id BIGINT NULL COMMENT ''被邀请人用户ID'' AFTER inviter_user_id', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='reward_rule_code')=0, 'ALTER TABLE invite_reward_record ADD COLUMN reward_rule_code VARCHAR(64) NULL COMMENT ''奖励规则编码'' AFTER rule_code', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='reward_type')=0, 'ALTER TABLE invite_reward_record ADD COLUMN reward_type VARCHAR(32) NOT NULL DEFAULT ''GROWTH_VALUE'' COMMENT ''奖励类型'' AFTER reward_rule_code', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='reward_value')=0, 'ALTER TABLE invite_reward_record ADD COLUMN reward_value INT NOT NULL DEFAULT 0 COMMENT ''奖励值'' AFTER reward_type', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='idempotency_key')=0, 'ALTER TABLE invite_reward_record ADD COLUMN idempotency_key VARCHAR(128) NULL COMMENT ''业务幂等键，用于防止重复处理'' AFTER reward_biz_no', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='triggered_at')=0, 'ALTER TABLE invite_reward_record ADD COLUMN triggered_at DATETIME NULL COMMENT ''奖励触发时间'' AFTER reward_status', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND column_name='issued_at')=0, 'ALTER TABLE invite_reward_record ADD COLUMN issued_at DATETIME NULL COMMENT ''发放时间'' AFTER granted_at', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 通过关系和规则表回填历史台账新字段，已有非空/非零值保持不变。
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

-- 将历史 GRANTED 状态统一迁移为新状态命名 ISSUED。
UPDATE invite_reward_record SET reward_status='ISSUED' WHERE reward_status='GRANTED' AND deleted=0;

-- 最后创建奖励幂等唯一索引；放在回填后避免历史 NULL 值影响迁移。
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='invite_reward_record' AND index_name='uk_invite_reward_idempotency')=0,
  'CREATE UNIQUE INDEX uk_invite_reward_idempotency ON invite_reward_record(idempotency_key, deleted)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
