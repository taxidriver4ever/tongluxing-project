CREATE TABLE IF NOT EXISTS trip_favorite (
  id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  trip_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_trip_favorite_user_trip (user_id, trip_id),
  KEY idx_trip_favorite_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
