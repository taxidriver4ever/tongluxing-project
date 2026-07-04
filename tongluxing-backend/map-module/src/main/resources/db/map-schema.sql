CREATE TABLE IF NOT EXISTS map_route_plan (
  id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  route_hash VARCHAR(64) NOT NULL,
  route_points_json JSON NOT NULL,
  route_result_json JSON NULL,
  provider_type VARCHAR(32) NOT NULL DEFAULT 'MOCK',
  plan_status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
  error_message VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_map_route_hash (route_hash, provider_type, deleted),
  KEY idx_map_route_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS map_location_search_log (
  id BIGINT NOT NULL,
  user_id BIGINT NULL,
  keyword VARCHAR(128) NULL,
  selected_name VARCHAR(128) NULL,
  selected_address VARCHAR(255) NULL,
  selected_latitude DECIMAL(10,6) NULL,
  selected_longitude DECIMAL(10,6) NULL,
  scene VARCHAR(32) NOT NULL,
  provider_type VARCHAR(32) NOT NULL DEFAULT 'MOCK',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_map_search_user_scene_time (user_id, scene, created_at),
  KEY idx_map_search_keyword (keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS map_geocode_cache (
  id BIGINT NOT NULL,
  location_hash VARCHAR(64) NOT NULL,
  address VARCHAR(255) NULL,
  latitude DECIMAL(10,6) NULL,
  longitude DECIMAL(10,6) NULL,
  geocode_result_json JSON NULL,
  provider_type VARCHAR(32) NOT NULL DEFAULT 'MOCK',
  expire_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_map_geocode_hash (location_hash, provider_type, deleted),
  KEY idx_map_geocode_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
