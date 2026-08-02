-- 用户邀请码：用户和邀请码值均有唯一索引，确保一人一码、全平台不重码。
CREATE TABLE IF NOT EXISTS invite_code (
  id BIGINT NOT NULL comment '记录主键',
  user_id BIGINT NOT NULL comment '平台用户ID',
  invite_code VARCHAR(16) NOT NULL comment '邀请编码',
  enabled_flag TINYINT NOT NULL DEFAULT 1 comment '是否启用：0否、1是',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_invite_code_user (user_id, deleted),
  UNIQUE KEY uk_invite_code_value (invite_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='邀请编码表';

-- 邀请关系：被邀请人唯一，request_id 辅助客户端重试幂等。
CREATE TABLE IF NOT EXISTS invite_relation (
  id BIGINT NOT NULL comment '记录主键',
  inviter_user_id BIGINT NOT NULL comment '邀请人用户ID',
  invitee_user_id BIGINT NOT NULL comment '被邀请人用户ID',
  invite_code VARCHAR(16) NULL comment '邀请编码',
  relation_status VARCHAR(16) NOT NULL DEFAULT 'REGISTERED' comment '关系状态',
  bind_source VARCHAR(32) NOT NULL DEFAULT 'MANUAL_CODE' comment '绑定来源',
  bind_source_value VARCHAR(64) NULL comment '绑定来源值',
  request_id VARCHAR(64) NULL comment '请求唯一标识，用于链路追踪或幂等控制',
  invitee_registered_at DATETIME NULL comment '被邀请人注册时间',
  bound_at DATETIME NOT NULL comment '邀请关系绑定时间',
  first_team_completed_at DATETIME NULL comment '首次完成组队行程时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_invite_relation_invitee (invitee_user_id, deleted),
  UNIQUE KEY uk_invite_relation_request (request_id, deleted),
  KEY idx_invite_relation_inviter (inviter_user_id, relation_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='邀请关系表';

-- 奖励台账：bizNo、idempotencyKey 和“关系+规则”三组唯一键共同防重。
CREATE TABLE IF NOT EXISTS invite_reward_record (
  id BIGINT NOT NULL comment '记录主键',
  relation_id BIGINT NOT NULL comment '关系ID',
  beneficiary_user_id BIGINT NOT NULL comment '受益人用户ID',
  inviter_user_id BIGINT NOT NULL comment '邀请人用户ID',
  invitee_user_id BIGINT NOT NULL comment '被邀请人用户ID',
  rule_code VARCHAR(64) NOT NULL comment '规则编码',
  reward_rule_code VARCHAR(64) NOT NULL comment '奖励规则编码',
  reward_type VARCHAR(32) NOT NULL DEFAULT 'GROWTH_VALUE' comment '奖励类型',
  reward_value INT NOT NULL DEFAULT 0 comment '奖励值',
  reward_biz_no VARCHAR(64) NOT NULL comment '奖励业务编号',
  idempotency_key VARCHAR(128) NOT NULL comment '业务幂等键，用于防止重复处理',
  reward_snapshot_json JSON NOT NULL comment '奖励快照JSON数据',
  reward_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' comment '奖励状态',
  triggered_at DATETIME NOT NULL comment '奖励触发时间',
  failure_reason VARCHAR(255) NULL comment '失败原因',
  granted_at DATETIME NULL comment '授予时间',
  issued_at DATETIME NULL comment '发放时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_invite_reward_biz (reward_biz_no, deleted),
  UNIQUE KEY uk_invite_reward_idempotency (idempotency_key, deleted),
  UNIQUE KEY uk_invite_reward_relation_rule (relation_id, rule_code, deleted),
  KEY idx_invite_reward_user (beneficiary_user_id, reward_status, created_at),
  KEY idx_invite_reward_invitee (invitee_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='邀请奖励记录表';

-- 可运营奖励规则：实时配置可覆盖代码中的保底奖励值。
CREATE TABLE IF NOT EXISTS invite_reward_rule (
  id BIGINT NOT NULL comment '记录主键',
  rule_code VARCHAR(64) NOT NULL comment '规则编码',
  reward_type VARCHAR(32) NOT NULL DEFAULT 'GROWTH_VALUE' comment '奖励类型',
  reward_value INT NOT NULL comment '奖励值',
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' comment '业务状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_invite_reward_rule_code (rule_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='邀请奖励规则表';


-- 内置初始规则使用 INSERT IGNORE，避免重复初始化覆盖运营已调整的数值。
INSERT IGNORE INTO invite_reward_rule
(id, rule_code, reward_type, reward_value, status, created_at, updated_at, deleted)
VALUES
(202607270001, 'INVITE_REGISTER_SUCCESS', 'GROWTH_VALUE', 50, 'ENABLED', NOW(), NOW(), 0),
(202607270002, 'INVITEE_FIRST_TEAM_COMPLETED', 'GROWTH_VALUE', 100, 'ENABLED', NOW(), NOW(), 0),
(202607270003, 'INVITE_STAGE_3', 'GROWTH_VALUE', 200, 'ENABLED', NOW(), NOW(), 0),
(202607270004, 'INVITE_STAGE_10', 'GROWTH_VALUE', 500, 'ENABLED', NOW(), NOW(), 0),
(202607270005, 'INVITE_STAGE_30', 'GROWTH_VALUE', 2000, 'ENABLED', NOW(), NOW(), 0),
(202607270006, 'INVITE_STAGE_50', 'GROWTH_VALUE', 5000, 'ENABLED', NOW(), NOW(), 0);
