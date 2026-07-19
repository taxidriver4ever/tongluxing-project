CREATE TABLE IF NOT EXISTS merchant_profile (
  id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  merchant_name VARCHAR(128) NOT NULL,
  category VARCHAR(32) NOT NULL,
  contact_name VARCHAR(64) NOT NULL,
  contact_phone_cipher VARCHAR(256) NOT NULL,
  contact_phone_mask VARCHAR(32) NOT NULL,
  province_code VARCHAR(16) NOT NULL DEFAULT '',
  city_code VARCHAR(16) NOT NULL DEFAULT '',
  address VARCHAR(255) NOT NULL,
  longitude DECIMAL(10,6) NULL,
  latitude DECIMAL(10,6) NULL,
  cover_image_key VARCHAR(512) NOT NULL DEFAULT '',
  description VARCHAR(1000) NOT NULL DEFAULT '',
  license_image_key VARCHAR(512) NOT NULL,
  qualification_json TEXT NULL,
  bank_account_cipher VARCHAR(512) NOT NULL DEFAULT '',
  bank_name VARCHAR(128) NOT NULL DEFAULT '',
  audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  merchant_level VARCHAR(16) NOT NULL DEFAULT 'L1',
  score DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  commission_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0800,
  rank_weight DECIMAL(8,4) NOT NULL DEFAULT 1.0000,
  exclusion_radius_km DECIMAL(8,2) NOT NULL DEFAULT 0.00,
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_merchant_user (user_id, deleted),
  KEY idx_merchant_city_category (city_code, category, status, deleted),
  KEY idx_merchant_audit (audit_status, deleted),
  KEY idx_merchant_level (merchant_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_product (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  product_name VARCHAR(128) NOT NULL,
  product_type VARCHAR(32) NOT NULL,
  original_price DECIMAL(10,2) NOT NULL,
  group_price DECIMAL(10,2) NOT NULL,
  ladder_price_json TEXT NULL,
  target_people INT NOT NULL,
  stock INT NOT NULL,
  valid_hours INT NOT NULL,
  min_settlement_price DECIMAL(10,2) NULL,
  image_keys_json TEXT NULL,
  description VARCHAR(1000) NOT NULL DEFAULT '',
  product_status VARCHAR(32) NOT NULL DEFAULT 'ON_SHELF',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_product_merchant (merchant_id, product_status, deleted),
  KEY idx_product_type (product_type, product_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_coupon_pool (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  coupon_name VARCHAR(128) NOT NULL,
  coupon_type VARCHAR(32) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  threshold_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  discount_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  discount_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0000,
  total_stock INT NOT NULL,
  used_stock INT NOT NULL DEFAULT 0,
  valid_days INT NOT NULL,
  settlement_mode VARCHAR(32) NOT NULL,
  audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  pool_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_coupon_pool_merchant (merchant_id, pool_status, deleted),
  KEY idx_coupon_pool_source (source_type, pool_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_reward_pool_config (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  enabled TINYINT NOT NULL DEFAULT 0,
  coupon_type VARCHAR(32) NOT NULL DEFAULT '',
  monthly_stock INT NOT NULL DEFAULT 0,
  used_stock INT NOT NULL DEFAULT 0,
  exposure_weight_bonus DECIMAL(8,4) NOT NULL DEFAULT 0.0000,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_reward_pool_merchant (merchant_id, deleted),
  KEY idx_reward_pool_enabled (enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_promotion_code (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  promotion_code VARCHAR(64) NOT NULL,
  channel_name VARCHAR(64) NOT NULL,
  scene VARCHAR(32) NOT NULL,
  qr_image_key VARCHAR(512) NOT NULL DEFAULT '',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  remark VARCHAR(255) NOT NULL DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_promotion_code (promotion_code, deleted),
  KEY idx_promotion_merchant (merchant_id, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_promotion_stats (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  promotion_code_id BIGINT NOT NULL,
  stat_date DATE NOT NULL,
  register_count BIGINT NOT NULL DEFAULT 0,
  coupon_claim_count BIGINT NOT NULL DEFAULT 0,
  coupon_verify_count BIGINT NOT NULL DEFAULT 0,
  order_count BIGINT NOT NULL DEFAULT 0,
  trade_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_promotion_stats_day (promotion_code_id, stat_date),
  KEY idx_stats_merchant_date (merchant_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_user_relation (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  promotion_code_id BIGINT NOT NULL,
  promotion_code VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  registered_at DATETIME NOT NULL,
  first_consumed_at DATETIME NULL,
  relation_status VARCHAR(32) NOT NULL DEFAULT 'BOUND',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_merchant_user_relation (user_id, deleted),
  KEY idx_merchant_relation (merchant_id, registered_at),
  KEY idx_promotion_relation (promotion_code_id, registered_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_audit_log (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  operator_id BIGINT NOT NULL,
  operation_type VARCHAR(32) NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT NOT NULL,
  before_snapshot TEXT NULL,
  after_snapshot TEXT NULL,
  remark VARCHAR(255) NOT NULL DEFAULT '',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_audit_merchant (merchant_id, created_at),
  KEY idx_audit_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 入驻审核结果与商家主体分离，支持驳回后修改重提并保留审核时间。
CREATE TABLE IF NOT EXISTS merchant_application_review (
  merchant_id BIGINT NOT NULL,
  reject_reason VARCHAR(255) NOT NULL DEFAULT '',
  reviewer_id BIGINT NULL,
  reviewed_at DATETIME NULL,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 收款信息只允许审核通过后填写，不进入首轮入驻资料。
CREATE TABLE IF NOT EXISTS merchant_settlement_account (
  merchant_id BIGINT NOT NULL,
  account_type VARCHAR(32) NOT NULL,
  account_name VARCHAR(128) NOT NULL,
  account_no_cipher VARCHAR(512) NOT NULL,
  bank_name VARCHAR(128) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_store (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  store_name VARCHAR(128) NOT NULL,
  address VARCHAR(255) NOT NULL,
  longitude DECIMAL(10,6) NOT NULL,
  latitude DECIMAL(10,6) NOT NULL,
  contact_phone_mask VARCHAR(32) NOT NULL,
  business_hours VARCHAR(128) NOT NULL,
  parking_info VARCHAR(255) NOT NULL DEFAULT '',
  store_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_store_merchant (merchant_id, store_status, deleted),
  KEY idx_store_location (longitude, latitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_coupon_offer (
  id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  store_id BIGINT NOT NULL,
  coupon_name VARCHAR(128) NOT NULL,
  cover_image_key VARCHAR(512) NOT NULL DEFAULT '',
  description VARCHAR(1000) NOT NULL DEFAULT '',
  category VARCHAR(32) NOT NULL,
  original_price DECIMAL(10,2) NOT NULL,
  sale_price DECIMAL(10,2) NOT NULL,
  stock INT NOT NULL,
  sold_count INT NOT NULL DEFAULT 0,
  limit_count INT NOT NULL DEFAULT 1,
  group_enabled TINYINT NOT NULL DEFAULT 0,
  group_people INT NULL,
  group_timeout_hours INT NULL,
  publish_time DATETIME NOT NULL,
  expire_time DATETIME NOT NULL,
  use_start_time DATETIME NOT NULL,
  use_end_time DATETIME NOT NULL,
  reservation_required TINYINT NOT NULL DEFAULT 0,
  refundable TINYINT NOT NULL DEFAULT 1,
  holiday_available TINYINT NOT NULL DEFAULT 1,
  stackable TINYINT NOT NULL DEFAULT 0,
  use_instructions VARCHAR(1000) NOT NULL DEFAULT '',
  audit_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  reject_reason VARCHAR(255) NOT NULL DEFAULT '',
  reviewer_id BIGINT NULL,
  reviewed_at DATETIME NULL,
  offer_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_offer_merchant (merchant_id, audit_status, deleted),
  KEY idx_offer_market (audit_status, offer_status, category, publish_time, expire_time),
  KEY idx_offer_store (store_id, audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
