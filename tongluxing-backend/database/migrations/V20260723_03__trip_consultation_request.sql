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
