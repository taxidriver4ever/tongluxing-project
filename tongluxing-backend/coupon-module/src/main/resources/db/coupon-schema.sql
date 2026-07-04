CREATE TABLE IF NOT EXISTS coupon_template (
  id BIGINT NOT NULL, coupon_name VARCHAR(64) NOT NULL, coupon_type VARCHAR(24) NOT NULL,
  issuer_id BIGINT NULL, threshold_amount DECIMAL(10,2) NOT NULL DEFAULT 0,
  discount_amount DECIMAL(10,2) NOT NULL, scope_json JSON NOT NULL,
  validity_type VARCHAR(16) NOT NULL, valid_days INT NULL, valid_start_at DATETIME NULL,
  valid_end_at DATETIME NULL, total_quantity INT NOT NULL, claimed_quantity INT NOT NULL DEFAULT 0,
  per_user_limit INT NOT NULL DEFAULT 1, template_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
  KEY idx_coupon_template_status (template_status, valid_start_at, valid_end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS coupon_user (
  id BIGINT NOT NULL, user_id BIGINT NOT NULL, template_id BIGINT NOT NULL,
  source_type VARCHAR(24) NOT NULL, source_biz_id VARCHAR(64) NOT NULL,
  coupon_status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE', valid_start_at DATETIME NOT NULL,
  valid_end_at DATETIME NOT NULL, locked_order_id BIGINT NULL, used_order_id BIGINT NULL,
  used_at DATETIME NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
  UNIQUE KEY uk_coupon_user_source (user_id, template_id, source_type, source_biz_id, deleted),
  KEY idx_coupon_user_status (user_id, coupon_status, valid_end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
