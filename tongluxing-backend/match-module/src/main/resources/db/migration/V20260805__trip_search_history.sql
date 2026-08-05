CREATE TABLE IF NOT EXISTS trip_search_history (
    id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    keyword VARCHAR(80) NOT NULL,
    search_type VARCHAR(24) NOT NULL DEFAULT 'DESTINATION',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trip_search_history_user_keyword_type (user_id, keyword, search_type),
    KEY idx_trip_search_history_user_updated (user_id, updated_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
