CREATE TABLE IF NOT EXISTS chat_conversation (
  id BIGINT NOT NULL comment '记录主键',
  biz_type VARCHAR(32) NOT NULL comment '关联业务类型',
  biz_id BIGINT NOT NULL comment '关联业务单据标识',
  conversation_name VARCHAR(64) NOT NULL comment '会话名称',
  conversation_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' comment '会话状态',
  provider_type VARCHAR(32) NOT NULL DEFAULT 'MOCK' comment '外部服务提供方类型',
  provider_conversation_key VARCHAR(128) NULL comment '服务商会话标识或存储Key',
  last_message_id BIGINT NULL comment '最后消息ID',
  last_message_preview VARCHAR(120) NULL comment '最后消息预览',
  last_message_at DATETIME NULL comment '最后消息时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_biz (biz_type, biz_id, deleted),
  KEY idx_chat_last_time (conversation_status, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='聊天会话表';

CREATE TABLE IF NOT EXISTS chat_conversation_member (
  id BIGINT NOT NULL comment '记录主键',
  conversation_id BIGINT NOT NULL comment '会话ID',
  user_id BIGINT NOT NULL comment '平台用户ID',
  member_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' comment '成员角色',
  member_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' comment '成员状态',
  unread_count INT NOT NULL DEFAULT 0 comment '未读数量',
  muted_flag TINYINT(1) NOT NULL DEFAULT 0 comment '是否免打扰：0否、1是',
  pinned_flag TINYINT(1) NOT NULL DEFAULT 0 comment '是否置顶：0否、1是',
  last_read_message_id BIGINT NULL comment '最后已读消息ID',
  cleared_before_message_id BIGINT NULL comment '清除变更前消息ID',
  joined_at DATETIME NOT NULL comment '加入时间',
  exited_at DATETIME NULL comment '退出时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_member (conversation_id, user_id, deleted),
  KEY idx_chat_member_user (user_id, member_status),
  KEY idx_chat_member_conversation (conversation_id, member_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='聊天会话成员表';

CREATE TABLE IF NOT EXISTS chat_join_application (
  id BIGINT NOT NULL comment '记录主键',
  conversation_id BIGINT NOT NULL comment '会话ID',
  applicant_user_id BIGINT NOT NULL comment '申请人用户ID',
  application_message VARCHAR(120) NULL comment '申请说明',
  application_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' comment '申请状态',
  reviewer_user_id BIGINT NULL comment '审核人用户ID',
  reviewed_at DATETIME NULL comment '审核时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  KEY idx_chat_join_owner_queue (conversation_id, application_status, created_at),
  KEY idx_chat_join_applicant (applicant_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='聊天加入申请表';

CREATE TABLE IF NOT EXISTS chat_message (
  id BIGINT NOT NULL comment '记录主键',
  conversation_id BIGINT NOT NULL comment '会话ID',
  sender_user_id BIGINT NULL comment '发送人用户ID',
  message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' comment '消息类型',
  message_payload_json JSON NOT NULL comment '消息载荷JSON数据',
  message_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL' comment '消息状态',
  provider_message_key VARCHAR(128) NULL comment '服务商消息标识或存储Key',
  sent_at DATETIME NOT NULL comment '消息发送时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  KEY idx_chat_msg_conversation_time (conversation_id, sent_at),
  KEY idx_chat_msg_sender_time (sender_user_id, sent_at),
  UNIQUE KEY uk_chat_msg_provider (provider_message_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='聊天消息表';

CREATE TABLE IF NOT EXISTS message_risk (
  id BIGINT NOT NULL comment '记录主键',
  message_id BIGINT NOT NULL comment '消息ID',
  risk_level VARCHAR(16) NOT NULL comment '风险等级',
  risk_type VARCHAR(20) NOT NULL comment '风险类型',
  confidence INT NOT NULL DEFAULT 0 comment '置信度',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' comment '业务状态',
  matched_rule VARCHAR(80) NULL comment '命中规则',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_message_risk_message (message_id),
  KEY idx_message_risk_review (status, risk_level, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='消息风险表';

CREATE TABLE IF NOT EXISTS chat_group_item (
  id BIGINT NOT NULL comment '记录主键',
  conversation_id BIGINT NOT NULL comment '会话ID',
  item_type VARCHAR(24) NOT NULL COMMENT 'ANNOUNCEMENT/RESOURCE/POLL/REMINDER',
  title VARCHAR(120) NOT NULL comment '展示标题',
  content VARCHAR(2000) NULL comment '正文内容',
  payload_json JSON NULL comment '业务载荷JSON数据',
  item_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' comment '事项状态',
  creator_user_id BIGINT NOT NULL comment '创建人用户ID',
  created_at DATETIME NOT NULL comment '记录创建时间',
  updated_at DATETIME NOT NULL comment '记录最后更新时间',
  deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  KEY idx_chat_group_item (conversation_id,item_type,item_status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群公告、资料、投票与提醒';

CREATE TABLE IF NOT EXISTS chat_group_vote (
  id BIGINT NOT NULL comment '记录主键',
  item_id BIGINT NOT NULL comment '事项ID',
  option_key VARCHAR(64) NOT NULL comment '选项标识或存储Key',
  user_id BIGINT NOT NULL comment '平台用户ID',
  created_at DATETIME NOT NULL comment '记录创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_poll_user (item_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群投票记录';

CREATE TABLE IF NOT EXISTS chat_reminder_dispatch (
  item_id BIGINT NOT NULL comment '事项ID',
  dispatched_at DATETIME NOT NULL comment '分发时间',
  PRIMARY KEY (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程提醒去重发送记录';

CREATE TABLE IF NOT EXISTS trip_confirmation (
  id BIGINT NOT NULL comment '记录主键',
  trip_id BIGINT NOT NULL comment '行程ID',
  conversation_id BIGINT NOT NULL comment '会话ID',
  creator_user_id BIGINT NOT NULL comment '创建人用户ID',
  confirmation_status VARCHAR(20) NOT NULL DEFAULT 'OPEN' comment '确认状态',
  created_at DATETIME NOT NULL comment '记录创建时间',
  closed_at DATETIME NULL comment '关闭时间',
  PRIMARY KEY (id),
  KEY idx_trip_confirmation (trip_id,confirmation_status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程开始前成员确认批次';

CREATE TABLE IF NOT EXISTS trip_confirm_record (
  id BIGINT NOT NULL comment '记录主键',
  confirmation_id BIGINT NOT NULL comment '确认ID',
  trip_id BIGINT NOT NULL comment '行程ID',
  user_id BIGINT NOT NULL comment '平台用户ID',
  status VARCHAR(20) NOT NULL DEFAULT 'WAITING' comment '业务状态',
  reject_reason VARCHAR(300) NULL comment '驳回原因',
  confirm_time DATETIME NULL comment '确认时间',
  created_at DATETIME NOT NULL comment '记录创建时间',
  updated_at DATETIME NOT NULL comment '记录最后更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_trip_confirm_member (confirmation_id,user_id),
  KEY idx_trip_confirm_trip (trip_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程成员参加确认记录';

CREATE TABLE IF NOT EXISTS chat_member_location (
  id BIGINT NOT NULL comment '记录主键',
  conversation_id BIGINT NOT NULL comment '会话ID',
  user_id BIGINT NOT NULL comment '平台用户ID',
  latitude DECIMAL(10,7) NOT NULL comment '纬度坐标',
  longitude DECIMAL(10,7) NOT NULL comment '经度坐标',
  speed DECIMAL(8,2) NULL comment '速度',
  sharing_flag TINYINT(1) NOT NULL DEFAULT 1 comment '是否分账：0否、1是',
  recorded_at DATETIME NOT NULL comment '位置记录时间',
  updated_at DATETIME NOT NULL comment '记录最后更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_chat_member_location (conversation_id,user_id),
  KEY idx_chat_location_time (conversation_id,recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程群成员最新共享位置';

CREATE TABLE IF NOT EXISTS chat_report (
  id BIGINT NOT NULL comment '记录主键',
  conversation_id BIGINT NOT NULL comment '会话ID',
  reporter_user_id BIGINT NOT NULL comment '举报人用户ID',
  target_type VARCHAR(20) NOT NULL COMMENT 'CONVERSATION/MEMBER/MESSAGE',
  target_id VARCHAR(64) NOT NULL comment '目标ID',
  report_type VARCHAR(32) NOT NULL comment '举报类型',
  reason VARCHAR(500) NOT NULL comment '原因说明',
  evidence_json JSON NULL comment '举报或审核证据JSON数据',
  report_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' comment '举报状态',
  reviewer_id BIGINT NULL comment '审核人ID',
  review_note VARCHAR(500) NULL comment '审核备注',
  reviewed_at DATETIME NULL comment '审核时间',
  created_at DATETIME NOT NULL comment '记录创建时间',
  updated_at DATETIME NOT NULL comment '记录最后更新时间',
  PRIMARY KEY (id),
  KEY idx_chat_report_review (report_status,created_at),
  KEY idx_chat_report_conversation (conversation_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊、成员与消息举报';
