CREATE TABLE IF NOT EXISTS match_route_snapshot (
  id BIGINT NOT NULL,
  trip_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  vehicle_id BIGINT NOT NULL,
  start_name VARCHAR(128) NOT NULL,
  start_address VARCHAR(255) NULL,
  start_latitude DECIMAL(10,6) NOT NULL,
  start_longitude DECIMAL(10,6) NOT NULL,
  end_name VARCHAR(128) NOT NULL,
  end_address VARCHAR(255) NULL,
  end_latitude DECIMAL(10,6) NOT NULL,
  end_longitude DECIMAL(10,6) NOT NULL,
  route_points_json JSON NULL,
  route_distance INT NULL,
  route_duration INT NULL,
  departure_time DATETIME NOT NULL,
  travel_depth VARCHAR(16) NOT NULL,
  max_vehicle_count INT NOT NULL,
  public_flag TINYINT(1) NOT NULL DEFAULT 1,
  snapshot_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_snapshot_trip (trip_id, deleted),
  KEY idx_match_snapshot_public_time (public_flag, snapshot_status, departure_time),
  KEY idx_match_snapshot_start_end (start_latitude, start_longitude, end_latitude, end_longitude),
  KEY idx_match_snapshot_user (user_id, snapshot_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS match_result (
  id BIGINT NOT NULL,
  source_trip_id BIGINT NOT NULL,
  target_trip_id BIGINT NOT NULL,
  source_user_id BIGINT NOT NULL,
  target_user_id BIGINT NOT NULL,
  match_score INT NOT NULL,
  overlap_rate INT NOT NULL,
  distance_gap_meters INT NULL,
  departure_gap_minutes INT NULL,
  score_detail_json JSON NULL,
  result_status VARCHAR(20) NOT NULL DEFAULT 'VALID',
  calculated_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_pair (source_trip_id, target_trip_id, deleted),
  KEY idx_match_source_score (source_trip_id, result_status, match_score),
  KEY idx_match_target (target_trip_id, result_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS match_recommend_log (
  id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  trip_id BIGINT NOT NULL,
  target_trip_id BIGINT NULL,
  target_team_id BIGINT NULL,
  scene VARCHAR(32) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  request_id VARCHAR(64) NULL,
  extra_json JSON NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_match_log_user_time (user_id, created_at),
  KEY idx_match_log_trip_scene (trip_id, scene, action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_favorite (
  id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  trip_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_trip_favorite_user_trip (user_id, trip_id),
  KEY idx_trip_favorite_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_consultation_request (
  id BIGINT NOT NULL,
  trip_id BIGINT NOT NULL,
  trip_title VARCHAR(128) NOT NULL,
  sender_user_id BIGINT NOT NULL,
  receiver_user_id BIGINT NOT NULL,
  content VARCHAR(500) NOT NULL,
  request_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_trip_consult_pending (trip_id, sender_user_id, request_status),
  KEY idx_trip_consult_receiver (receiver_user_id, request_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
