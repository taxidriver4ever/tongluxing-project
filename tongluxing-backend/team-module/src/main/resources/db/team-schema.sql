CREATE TABLE IF NOT EXISTS team (
  id BIGINT NOT NULL,
  trip_id BIGINT NOT NULL,
  owner_user_id BIGINT NOT NULL,
  owner_vehicle_id BIGINT NOT NULL,
  team_name VARCHAR(64) NOT NULL,
  team_desc VARCHAR(255) NULL,
  start_name VARCHAR(128) NOT NULL,
  end_name VARCHAR(128) NOT NULL,
  departure_time DATETIME NOT NULL,
  max_member_count INT NOT NULL,
  current_member_count INT NOT NULL DEFAULT 1,
  join_mode VARCHAR(20) NOT NULL DEFAULT 'APPROVAL',
  team_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  public_flag TINYINT(1) NOT NULL DEFAULT 1,
  chat_conversation_id BIGINT NULL,
  notice VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_team_trip (trip_id, team_status),
  KEY idx_team_owner (owner_user_id, team_status),
  KEY idx_team_public_time (public_flag, team_status, departure_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS team_member (
  id BIGINT NOT NULL,
  team_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  vehicle_id BIGINT NULL,
  member_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
  member_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  joined_at DATETIME NOT NULL,
  exited_at DATETIME NULL,
  nickname_snapshot VARCHAR(64) NULL,
  vehicle_snapshot VARCHAR(128) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_team_member_user (team_id, user_id, deleted),
  KEY idx_team_member_team (team_id, member_status),
  KEY idx_team_member_user_status (user_id, member_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Compatibility migration: a user may join multiple future teams. Only RUNNING conflicts are checked in services.
set @team_drop_active_user_index = if(
  (select count(*) from information_schema.statistics
   where table_schema = database() and table_name = 'team_member'
     and index_name = 'uk_team_member_active_user') > 0,
  'alter table team_member drop index uk_team_member_active_user',
  'select 1'
);
prepare team_schema_stmt from @team_drop_active_user_index;
execute team_schema_stmt;
deallocate prepare team_schema_stmt;

set @team_drop_active_user_column = if(
  (select count(*) from information_schema.columns
   where table_schema = database() and table_name = 'team_member'
     and column_name = 'active_user_key') > 0,
  'alter table team_member drop column active_user_key',
  'select 1'
);
prepare team_schema_stmt from @team_drop_active_user_column;
execute team_schema_stmt;
deallocate prepare team_schema_stmt;

CREATE TABLE IF NOT EXISTS team_join_application (
  id BIGINT NOT NULL,
  team_id BIGINT NOT NULL,
  trip_id BIGINT NULL,
  applicant_user_id BIGINT NOT NULL,
  applicant_vehicle_id BIGINT NULL,
  reviewer_user_id BIGINT NULL,
  application_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  apply_message VARCHAR(255) NULL,
  join_question_json JSON NULL,
  review_message VARCHAR(255) NULL,
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_team_apply_applicant (applicant_user_id, application_status, created_at),
  KEY idx_team_apply_team (team_id, application_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS team_audit_log (
  id BIGINT NOT NULL,
  team_id BIGINT NOT NULL,
  operator_user_id BIGINT NOT NULL,
  operation_type VARCHAR(32) NOT NULL,
  before_json JSON NULL,
  after_json JSON NULL,
  remark VARCHAR(255) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_team_audit_team_time (team_id, created_at),
  KEY idx_team_audit_operator_time (operator_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
