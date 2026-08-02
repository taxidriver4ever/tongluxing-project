CREATE TABLE IF NOT EXISTS merchant_profile (
  id BIGINT NOT NULL comment '记录主键',
  user_id BIGINT NOT NULL comment '平台用户ID',
  merchant_name VARCHAR(128) NOT NULL comment '商家名称',
  category VARCHAR(32) NOT NULL comment '分类',
  contact_name VARCHAR(64) NOT NULL comment '联系人名称',
  contact_phone_cipher VARCHAR(256) NOT NULL comment '联系人手机号加密密文',
  contact_phone_mask VARCHAR(32) NOT NULL comment '联系人手机号脱敏展示值',
  province_code VARCHAR(16) NOT NULL DEFAULT '' comment '省级行政区划编码',
  city_code VARCHAR(16) NOT NULL DEFAULT '' comment '城市编码',
  address VARCHAR(255) NOT NULL comment '地址',
  longitude DECIMAL(10,6) NULL comment '经度坐标',
  latitude DECIMAL(10,6) NULL comment '纬度坐标',
  cover_image_key VARCHAR(512) NOT NULL DEFAULT '' comment '封面图在对象存储中的文件Key',
  description VARCHAR(1000) NOT NULL DEFAULT '' comment '详细说明',
  license_image_key VARCHAR(512) NOT NULL comment '驾驶证图片标识或存储Key',
  qualification_json TEXT NULL comment '资质JSON数据',
  bank_account_cipher VARCHAR(512) NOT NULL DEFAULT '' comment '银行账号加密密文',
  bank_name VARCHAR(128) NOT NULL DEFAULT '' comment '银行名称',
  audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' comment '审核状态',
  merchant_level VARCHAR(16) NOT NULL DEFAULT 'L1' comment '商家等级',
  score DECIMAL(8,2) NOT NULL DEFAULT 0.00 comment '综合评分值',
  commission_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0800 comment '佣金比例',
  rank_weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000 comment '排序权重',
  exclusion_radius_km DECIMAL(8,2) NOT NULL DEFAULT 0.00 comment '排除半径，单位为公里',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' comment '业务状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_merchant_user (user_id, deleted),
  KEY idx_merchant_city_category (city_code, category, status, deleted),
  KEY idx_merchant_audit (audit_status, deleted),
  KEY idx_merchant_level (merchant_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家资料表';

CREATE TABLE IF NOT EXISTS merchant_product (
  id BIGINT NOT NULL comment '记录主键',
  merchant_id BIGINT NOT NULL comment '商家ID',
  product_name VARCHAR(128) NOT NULL comment '商品名称',
  product_type VARCHAR(32) NOT NULL comment '商品类型',
  original_price DECIMAL(10,2) NOT NULL comment '原始价格',
  group_price DECIMAL(10,2) NOT NULL comment '拼团价格',
  ladder_price_json TEXT NULL comment '阶梯价格JSON数据',
  target_people INT NOT NULL comment '目标人数',
  stock INT NOT NULL comment '库存',
  valid_hours INT NOT NULL comment '有效，单位为小时',
  min_settlement_price DECIMAL(10,2) NULL comment '最低结算价格',
  image_keys_json TEXT NULL comment '图片Key列表JSON数据',
  description VARCHAR(1000) NOT NULL DEFAULT '' comment '详细说明',
  product_status VARCHAR(32) NOT NULL DEFAULT 'ON_SHELF' comment '商品状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  KEY idx_product_merchant (merchant_id, product_status, deleted),
  KEY idx_product_type (product_type, product_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家商品表';

CREATE TABLE IF NOT EXISTS merchant_coupon_pool (
  id BIGINT NOT NULL comment '记录主键',
  merchant_id BIGINT NOT NULL comment '商家ID',
  coupon_name VARCHAR(128) NOT NULL comment '优惠券名称',
  coupon_type VARCHAR(32) NOT NULL comment '优惠券类型',
  source_type VARCHAR(32) NOT NULL comment '来源类型',
  threshold_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 comment '门槛金额',
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 comment '优惠金额',
  discount_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0000 comment '优惠比例',
  total_stock INT NOT NULL comment '总计库存',
  used_stock INT NOT NULL DEFAULT 0 comment '使用库存',
  valid_days INT NOT NULL comment '有效，单位为天',
  settlement_mode VARCHAR(32) NOT NULL comment '结算模式',
  audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' comment '审核状态',
  pool_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' comment '资源池状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  KEY idx_coupon_pool_merchant (merchant_id, pool_status, deleted),
  KEY idx_coupon_pool_source (source_type, pool_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家优惠券池表';

CREATE TABLE IF NOT EXISTS merchant_reward_pool_config (
  id BIGINT NOT NULL comment '记录主键',
  merchant_id BIGINT NOT NULL comment '商家ID',
  enabled TINYINT NOT NULL DEFAULT 0 comment '启用',
  coupon_type VARCHAR(32) NOT NULL DEFAULT '' comment '优惠券类型',
  monthly_stock INT NOT NULL DEFAULT 0 comment '月度库存',
  used_stock INT NOT NULL DEFAULT 0 comment '使用库存',
  exposure_weight_bonus DECIMAL(8,4) NOT NULL DEFAULT 0.0000 comment '曝光权重加成',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_reward_pool_merchant (merchant_id, deleted),
  KEY idx_reward_pool_enabled (enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家奖励池配置表';

CREATE TABLE IF NOT EXISTS merchant_promotion_code (
  id BIGINT NOT NULL comment '记录主键',
  merchant_id BIGINT NOT NULL comment '商家ID',
  promotion_code VARCHAR(64) NOT NULL comment '推广编码',
  channel_name VARCHAR(64) NOT NULL comment '渠道名称',
  scene VARCHAR(32) NOT NULL comment '业务场景',
  qr_image_key VARCHAR(512) NOT NULL DEFAULT '' comment '二维码图片标识或存储Key',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' comment '业务状态',
  remark VARCHAR(255) NOT NULL DEFAULT '' comment '业务备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_promotion_code (promotion_code, deleted),
  KEY idx_promotion_merchant (merchant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家推广编码表';

CREATE TABLE IF NOT EXISTS merchant_promotion_stats (
  id BIGINT NOT NULL comment '记录主键',
  merchant_id BIGINT NOT NULL comment '商家ID',
  promotion_code_id BIGINT NOT NULL comment '推广编码ID',
  stat_date DATE NOT NULL comment '统计日期',
  register_count BIGINT NOT NULL DEFAULT 0 comment '注册数量',
  coupon_claim_count BIGINT NOT NULL DEFAULT 0 comment '优惠券领取数量',
  coupon_verify_count BIGINT NOT NULL DEFAULT 0 comment '优惠券核销数量',
  order_count BIGINT NOT NULL DEFAULT 0 comment '订单数量',
  trade_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 comment '交易金额',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_promotion_stats_day (promotion_code_id, stat_date),
  KEY idx_stats_merchant_date (merchant_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家推广统计表';

CREATE TABLE IF NOT EXISTS merchant_user_relation (
  id BIGINT NOT NULL comment '记录主键',
  merchant_id BIGINT NOT NULL comment '商家ID',
  promotion_code_id BIGINT NOT NULL comment '推广编码ID',
  promotion_code VARCHAR(64) NOT NULL comment '推广编码',
  user_id BIGINT NOT NULL comment '平台用户ID',
  registered_at DATETIME NOT NULL comment '注册时间',
  first_consumed_at DATETIME NULL comment '首次产生消费时间',
  relation_status VARCHAR(32) NOT NULL DEFAULT 'BOUND' comment '关系状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_merchant_user_relation (user_id, deleted),
  KEY idx_merchant_relation (merchant_id, registered_at),
  KEY idx_promotion_relation (promotion_code_id, registered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家用户关系表';

CREATE TABLE IF NOT EXISTS merchant_audit_log (
  id BIGINT NOT NULL comment '记录主键',
  merchant_id BIGINT NOT NULL comment '商家ID',
  operator_id BIGINT NOT NULL comment '管理员ID',
  operation_type VARCHAR(32) NOT NULL comment '操作类型',
  target_type VARCHAR(32) NOT NULL comment '目标类型',
  target_id BIGINT NOT NULL comment '目标ID',
  before_snapshot TEXT NULL comment '操作前数据快照',
  after_snapshot TEXT NULL comment '操作后数据快照',
  remark VARCHAR(255) NOT NULL DEFAULT '' comment '业务备注',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  PRIMARY KEY (id),
  KEY idx_audit_merchant (merchant_id, created_at),
  KEY idx_audit_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家审核日志表';

-- 入驻审核结果与商家主体分离，支持驳回后修改重提并保留审核时间。
CREATE TABLE IF NOT EXISTS merchant_application_review (
  merchant_id BIGINT NOT NULL comment '商家ID',
  reject_reason VARCHAR(255) NOT NULL DEFAULT '' comment '驳回原因',
  reviewer_id BIGINT NULL comment '审核人ID',
  reviewed_at DATETIME NULL comment '审核时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  PRIMARY KEY (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家申请审核表';

-- 普通商户升级为平台合作商的独立申请，不与首次商户认证混用。
CREATE TABLE IF NOT EXISTS merchant_partner_application (
  merchant_id BIGINT NOT NULL comment '商家ID',
  application_reason VARCHAR(500) NOT NULL comment '申请原因',
  cooperation_categories VARCHAR(255) NOT NULL DEFAULT '' comment '合作经营类别',
  planned_monthly_stock INT NOT NULL DEFAULT 0 comment '计划月度库存数量',
  application_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' comment '申请状态',
  reject_reason VARCHAR(255) NOT NULL DEFAULT '' comment '驳回原因',
  reviewer_id BIGINT NULL comment '审核人ID',
  reviewed_at DATETIME NULL comment '审核时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (merchant_id),
  KEY idx_partner_application_status (application_status, updated_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家合作入驻申请表';

CREATE TABLE IF NOT EXISTS merchant_partner_cancellation (
  merchant_id BIGINT NOT NULL comment '商家ID',
  cancellation_reason VARCHAR(500) NOT NULL comment '合作终止原因',
  cancellation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' comment '合作终止申请状态',
  reject_reason VARCHAR(255) NOT NULL DEFAULT '' comment '驳回原因',
  reviewer_id BIGINT NULL comment '审核人ID',
  requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '申请时间',
  reviewed_at DATETIME NULL comment '审核时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (merchant_id),
  KEY idx_partner_cancellation_status (cancellation_status, updated_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家合作终止申请表';

-- 收款信息只允许审核通过后填写，不进入首轮入驻资料。
CREATE TABLE IF NOT EXISTS merchant_settlement_account (
  merchant_id BIGINT NOT NULL comment '商家ID',
  account_type VARCHAR(32) NOT NULL comment '账号类型',
  account_name VARCHAR(128) NOT NULL comment '账号名称',
  account_no_cipher VARCHAR(512) NOT NULL comment '账号NO加密密文',
  bank_name VARCHAR(128) NOT NULL comment '银行名称',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家结算账号表';

CREATE TABLE IF NOT EXISTS merchant_store (
  id BIGINT NOT NULL comment '记录主键',
  merchant_id BIGINT NOT NULL comment '商家ID',
  store_name VARCHAR(128) NOT NULL comment '门店名称',
  address VARCHAR(255) NOT NULL comment '地址',
  longitude DECIMAL(10,6) NOT NULL comment '经度坐标',
  latitude DECIMAL(10,6) NOT NULL comment '纬度坐标',
  contact_phone_mask VARCHAR(32) NOT NULL comment '联系人手机号脱敏展示值',
  business_hours VARCHAR(128) NOT NULL comment '营业，单位为小时',
  parking_info VARCHAR(255) NOT NULL DEFAULT '' comment '停车信息',
  store_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' comment '门店状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  KEY idx_store_merchant (merchant_id, store_status, deleted),
  KEY idx_store_location (longitude, latitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家门店表';

CREATE TABLE IF NOT EXISTS merchant_coupon_offer (
  id BIGINT NOT NULL comment '记录主键',
  merchant_id BIGINT NOT NULL comment '商家ID',
  store_id BIGINT NOT NULL comment '门店ID',
  coupon_name VARCHAR(128) NOT NULL comment '优惠券名称',
  cover_image_key VARCHAR(512) NOT NULL DEFAULT '' comment '封面图在对象存储中的文件Key',
  description VARCHAR(1000) NOT NULL DEFAULT '' comment '详细说明',
  category VARCHAR(32) NOT NULL comment '分类',
  original_price DECIMAL(10,2) NOT NULL comment '原始价格',
  sale_price DECIMAL(10,2) NOT NULL comment '销售价格',
  stock INT NOT NULL comment '库存',
  sold_count INT NOT NULL DEFAULT 0 comment '已售数量',
  limit_count INT NOT NULL DEFAULT 1 comment '限制数量',
  group_enabled TINYINT NOT NULL DEFAULT 0 comment '是否启用拼团',
  group_people INT NULL comment '拼团人数',
  group_timeout_hours INT NULL comment '拼团超时时长，单位为小时',
  publish_time DATETIME NOT NULL comment '发布时间',
  expire_time DATETIME NOT NULL comment '过期时间',
  use_start_time DATETIME NOT NULL comment '可使用开始时间',
  use_end_time DATETIME NOT NULL comment '可使用结束时间',
  reservation_required TINYINT NOT NULL DEFAULT 0 comment '预约要求',
  refundable TINYINT NOT NULL DEFAULT 1 comment '可退款',
  holiday_available TINYINT NOT NULL DEFAULT 1 comment '节假日可用',
  stackable TINYINT NOT NULL DEFAULT 0 comment '可叠加',
  use_instructions VARCHAR(1000) NOT NULL DEFAULT '' comment '优惠券使用说明',
  audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' comment '审核状态',
  reject_reason VARCHAR(255) NOT NULL DEFAULT '' comment '驳回原因',
  reviewer_id BIGINT NULL comment '审核人ID',
  reviewed_at DATETIME NULL comment '审核时间',
  offer_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' comment '商品方案状态',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
  PRIMARY KEY (id),
  KEY idx_offer_merchant (merchant_id, audit_status, deleted),
  KEY idx_offer_market (audit_status, offer_status, category, publish_time, expire_time),
  KEY idx_offer_store (store_id, audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家优惠券商品方案表';
