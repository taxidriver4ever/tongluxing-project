-- 同路行数据库：创建数据库并全量重建全部业务表
-- 适用：MySQL 8.0+
--
-- 警告：
--   1. 本脚本会创建并切换到 tongluxing 数据库。
--   2. 本脚本会永久删除 tongluxing 中下方列出的全部业务表及其数据。
--   3. 执行前务必确认已完成备份，并使用具有 CREATE、DROP、ALTER、INDEX 权限的 MySQL 账号。
--   4. 建表完成后，再执行 02_create_test_users.sql 创建联调账号。

CREATE DATABASE IF NOT EXISTS `tongluxing`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `tongluxing`;
SELECT DATABASE() AS current_database;
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `sos_event`;
DROP TABLE IF EXISTS `admin_compensation_task`;
DROP TABLE IF EXISTS `admin_operation_config_version`;
DROP TABLE IF EXISTS `admin_operation_config`;
DROP TABLE IF EXISTS `admin_audit_log`;
DROP TABLE IF EXISTS `admin_operator_role`;
DROP TABLE IF EXISTS `admin_role`;
DROP TABLE IF EXISTS `admin_operator`;
DROP TABLE IF EXISTS `notify_delivery_log`;
DROP TABLE IF EXISTS `notify_template`;
DROP TABLE IF EXISTS `notify_message`;
DROP TABLE IF EXISTS `customer_service_ticket_message`;
DROP TABLE IF EXISTS `customer_service_ticket`;
DROP TABLE IF EXISTS `assessment_manual_adjustment`;
DROP TABLE IF EXISTS `assessment_merchant_score_item`;
DROP TABLE IF EXISTS `assessment_merchant_score`;
DROP TABLE IF EXISTS `assessment_level_mapping`;
DROP TABLE IF EXISTS `verification_compensation_task`;
DROP TABLE IF EXISTS `verification_reversal_request`;
DROP TABLE IF EXISTS `verification_record`;
DROP TABLE IF EXISTS `verification_code`;
DROP TABLE IF EXISTS `payment_profit_sharing_record`;
DROP TABLE IF EXISTS `payment_refund_record`;
DROP TABLE IF EXISTS `payment_record`;
DROP TABLE IF EXISTS `order_compensation_task`;
DROP TABLE IF EXISTS `order_item`;
DROP TABLE IF EXISTS `order_trade`;
DROP TABLE IF EXISTS `groupbuy_participant`;
DROP TABLE IF EXISTS `groupbuy_activity`;
DROP TABLE IF EXISTS `merchant_coupon_offer`;
DROP TABLE IF EXISTS `merchant_store`;
DROP TABLE IF EXISTS `merchant_settlement_account`;
DROP TABLE IF EXISTS `merchant_partner_cancellation`;
DROP TABLE IF EXISTS `merchant_partner_application`;
DROP TABLE IF EXISTS `merchant_application_review`;
DROP TABLE IF EXISTS `merchant_audit_log`;
DROP TABLE IF EXISTS `merchant_user_relation`;
DROP TABLE IF EXISTS `merchant_promotion_stats`;
DROP TABLE IF EXISTS `merchant_promotion_code`;
DROP TABLE IF EXISTS `merchant_reward_pool_config`;
DROP TABLE IF EXISTS `merchant_coupon_pool`;
DROP TABLE IF EXISTS `merchant_product`;
DROP TABLE IF EXISTS `merchant_profile`;
DROP TABLE IF EXISTS `trip_track_anomaly`;
DROP TABLE IF EXISTS `trip_track_summary`;
DROP TABLE IF EXISTS `trip_track_source_switch`;
DROP TABLE IF EXISTS `trip_waypoint_arrival`;
DROP TABLE IF EXISTS `trip_route_plan_version`;
DROP TABLE IF EXISTS `trip_track_point`;
DROP TABLE IF EXISTS `trip_execution_member`;
DROP TABLE IF EXISTS `trip_execution`;
DROP TABLE IF EXISTS `trip_member_distance_alert`;
DROP TABLE IF EXISTS `trip_mileage_settlement`;
DROP TABLE IF EXISTS `driver_track_deviation_record`;
DROP TABLE IF EXISTS `driver_track_distance_record`;
DROP TABLE IF EXISTS `driver_track_record`;
DROP TABLE IF EXISTS `file_storage`;
DROP TABLE IF EXISTS `trip_consultation_request`;
DROP TABLE IF EXISTS `trip_favorite`;
DROP TABLE IF EXISTS `match_recommend_log`;
DROP TABLE IF EXISTS `match_result`;
DROP TABLE IF EXISTS `match_route_snapshot`;
DROP TABLE IF EXISTS `chat_report`;
DROP TABLE IF EXISTS `chat_member_location`;
DROP TABLE IF EXISTS `trip_confirm_record`;
DROP TABLE IF EXISTS `trip_confirmation`;
DROP TABLE IF EXISTS `chat_reminder_dispatch`;
DROP TABLE IF EXISTS `chat_group_vote`;
DROP TABLE IF EXISTS `chat_group_item`;
DROP TABLE IF EXISTS `message_risk`;
DROP TABLE IF EXISTS `chat_message`;
DROP TABLE IF EXISTS `chat_join_application`;
DROP TABLE IF EXISTS `chat_conversation_member`;
DROP TABLE IF EXISTS `chat_conversation`;
DROP TABLE IF EXISTS `team_audit_log`;
DROP TABLE IF EXISTS `team_join_application`;
DROP TABLE IF EXISTS `team_member`;
DROP TABLE IF EXISTS `team`;
DROP TABLE IF EXISTS `map_geocode_cache`;
DROP TABLE IF EXISTS `map_location_catalog`;
DROP TABLE IF EXISTS `map_location_search_log`;
DROP TABLE IF EXISTS `map_route_plan`;
DROP TABLE IF EXISTS `trip_draft`;
DROP TABLE IF EXISTS `trip_audit_log`;
DROP TABLE IF EXISTS `trip_member_snapshot`;
DROP TABLE IF EXISTS `trip_waypoint`;
DROP TABLE IF EXISTS `trip_route`;
DROP TABLE IF EXISTS `trip`;
DROP TABLE IF EXISTS `vehicle_audit_log`;
DROP TABLE IF EXISTS `vehicle_certification_image`;
DROP TABLE IF EXISTS `vehicle_certification`;
DROP TABLE IF EXISTS `vehicle_profile`;
DROP TABLE IF EXISTS `coupon_user`;
DROP TABLE IF EXISTS `coupon_template`;
DROP TABLE IF EXISTS `invite_reward_rule`;
DROP TABLE IF EXISTS `invite_reward_record`;
DROP TABLE IF EXISTS `invite_relation`;
DROP TABLE IF EXISTS `invite_code`;
DROP TABLE IF EXISTS `growth_user_badge`;
DROP TABLE IF EXISTS `growth_badge`;
DROP TABLE IF EXISTS `growth_level_rule`;
DROP TABLE IF EXISTS `growth_log`;
DROP TABLE IF EXISTS `growth_account`;
DROP TABLE IF EXISTS `user_follow`;
DROP TABLE IF EXISTS `user_statistics`;
DROP TABLE IF EXISTS `user_privacy_setting`;
DROP TABLE IF EXISTS `user_driving_license_certification`;
DROP TABLE IF EXISTS `user_profile`;
DROP TABLE IF EXISTS `auth_user_role`;
DROP TABLE IF EXISTS `auth_password_credential`;
DROP TABLE IF EXISTS `auth_device_binding`;
DROP TABLE IF EXISTS `auth_login_log`;
DROP TABLE IF EXISTS `auth_sms_log`;
DROP TABLE IF EXISTS `auth_account`;

-- ============================================================================
-- auth-module
-- source: auth-module/src/main/resources/db/auth-schema.sql
-- ============================================================================
create table if not exists auth_account (
                                            id bigint primary key,
                                            user_id bigint not null,
                                            phone varchar(20) not null,
                                            account_status tinyint not null default 1 comment '1 normal, 2 disabled',
                                            mini_invite_onboarding_completed tinyint not null default 0 comment '0 pending, 1 completed',
                                            last_login_time datetime null,
                                            last_login_ip varchar(64) null,
                                            created_at datetime not null,
                                            updated_at datetime not null,
                                            deleted tinyint not null default 0,
                                            unique key uk_auth_account_phone (phone),
                                            unique key uk_auth_account_user_id (user_id),
                                            key idx_auth_account_status (account_status)
);

create table if not exists auth_sms_log (
                                            id bigint primary key,
                                            phone varchar(20) not null,
                                            scene varchar(32) not null,
                                            send_status tinyint not null comment '1 success, 2 failed',
                                            provider varchar(64) null,
                                            error_message varchar(255) null,
                                            created_at datetime not null,
                                            key idx_auth_sms_log_phone_scene (phone, scene),
                                            key idx_auth_sms_log_created_at (created_at)
);

create table if not exists auth_login_log (
                                              id bigint primary key,
                                              user_id bigint null,
                                              phone varchar(20) null,
                                              action_type varchar(32) not null comment 'login/password_login/wx_phone_login/app_login/logout/refresh',
                                              device_id varchar(128) null,
                                              ip varchar(64) null,
                                              success tinyint not null comment '1 success, 0 failed',
                                              message varchar(255) null,
                                              created_at datetime not null,
                                              key idx_auth_login_log_user_id (user_id),
                                              key idx_auth_login_log_phone (phone),
                                              key idx_auth_login_log_action_type (action_type),
                                              key idx_auth_login_log_created_at (created_at)
);

create table if not exists auth_device_binding (
                                                   id bigint primary key,
                                                   user_id bigint not null,
                                                   phone varchar(20) not null,
                                                   client_type varchar(32) not null,
                                                   device_id varchar(128) not null,
                                                   device_name varchar(128) null,
                                                   platform varchar(32) null,
                                                   bind_status tinyint not null default 1 comment '1 normal, 2 unbound, 3 risk frozen',
                                                   last_login_time datetime null,
                                                   last_login_ip varchar(64) null,
                                                   created_at datetime not null,
                                                   updated_at datetime not null,
                                                   deleted tinyint not null default 0,
                                                   unique key uk_auth_device_user_client_device (user_id, client_type, device_id, deleted),
                                                   key idx_auth_device_user_client (user_id, client_type),
                                                   key idx_auth_device_phone (phone)
);

create table if not exists auth_password_credential (
                                                        id bigint primary key,
                                                        user_id bigint not null,
                                                        password_hash varchar(255) not null,
                                                        password_version varchar(32) not null default 'BCRYPT',
                                                        password_status tinyint not null default 1 comment '1 normal, 2 frozen or unavailable',
                                                        last_set_time datetime not null,
                                                        created_at datetime not null,
                                                        updated_at datetime not null,
                                                        deleted tinyint not null default 0,
                                                        unique key uk_auth_password_user (user_id, deleted)
);

create table if not exists auth_user_role (
                                              user_id bigint not null,
                                              role_code varchar(32) not null,
                                              granted_at datetime not null default current_timestamp,
                                              primary key (user_id, role_code)
);

-- ============================================================================
-- user-module
-- source: user-module/src/main/resources/db/user-schema.sql
-- ============================================================================
create table if not exists user_profile (
                                            id bigint not null,
                                            user_id bigint not null,
                                            tongluxing_id varchar(32) not null,
                                            nickname varchar(32) not null default '',
                                            avatar_image_key varchar(512) not null default '',
                                            gender tinyint not null default 0,
                                            birthday date null,
                                            city_code varchar(16) null,
                                            city_name varchar(64) null,
                                            bio varchar(200) null,
                                            profile_status varchar(16) not null default 'ACTIVE',
                                            created_at datetime not null default current_timestamp,
                                            updated_at datetime not null default current_timestamp on update current_timestamp,
                                            deleted tinyint not null default 0,
                                            primary key (id),
                                            unique key uk_user_profile_user (user_id, deleted),
                                            unique key uk_user_profile_tongluxing_id (tongluxing_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists user_driving_license_certification (
                                                                  id bigint not null,
                                                                  user_id bigint not null,
                                                                  holder_name_cipher varchar(256) not null,
                                                                  license_no_cipher varchar(512) not null,
                                                                  license_no_mask varchar(32) not null,
                                                                  vehicle_class varchar(32) not null,
                                                                  first_issue_date date null,
                                                                  valid_from date null,
                                                                  valid_to date null,
                                                                  issuing_authority varchar(128) null,
                                                                  license_front_image_key varchar(512) not null,
                                                                  license_back_image_key varchar(512) null,
                                                                  recognition_source varchar(32) not null default 'MINIPROGRAM_OCR',
                                                                  certification_status varchar(20) not null default 'PENDING',
                                                                  reject_reason varchar(255) null,
                                                                  reviewer_id bigint null,
                                                                  submitted_at datetime not null,
                                                                  reviewed_at datetime null,
                                                                  created_at datetime not null default current_timestamp,
                                                                  updated_at datetime not null default current_timestamp on update current_timestamp,
                                                                  deleted tinyint not null default 0,
                                                                  primary key (id),
                                                                  key idx_driver_cert_user_submit (user_id, submitted_at),
                                                                  key idx_driver_cert_status_submit (certification_status, submitted_at),
                                                                  key idx_driver_cert_reviewer (reviewer_id, reviewed_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists user_privacy_setting (
                                                    id bigint not null,
                                                    user_id bigint not null,
                                                    profile_visibility varchar(16) not null default 'PUBLIC',
                                                    vehicle_visibility varchar(16) not null default 'TEAM_ONLY',
                                                    invite_enabled_flag tinyint not null default 1,
                                                    city_visible_flag tinyint not null default 1,
                                                    bio_visible_flag tinyint not null default 1,
                                                    trip_stats_visible_flag tinyint not null default 1,
                                                    level_visible_flag tinyint not null default 1,
                                                    location_enabled_flag tinyint not null default 1,
                                                    notification_enabled_flag tinyint not null default 1,
                                                    created_at datetime not null default current_timestamp,
                                                    updated_at datetime not null default current_timestamp on update current_timestamp,
                                                    deleted tinyint not null default 0,
                                                    primary key (id),
                                                    unique key uk_user_privacy_user (user_id, deleted)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists user_statistics (
                                               user_id bigint not null,
                                               total_trip_count int not null default 0,
                                               total_distance_meters bigint not null default 0,
                                               total_duration_minutes bigint not null default 0,
                                               completed_waypoint_count int not null default 0,
                                               updated_at datetime not null default current_timestamp on update current_timestamp,
                                               primary key (user_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists user_follow (
                                           id bigint not null,
                                           follower_user_id bigint not null,
                                           followed_user_id bigint not null,
                                           created_at datetime not null default current_timestamp,
                                           primary key (id),
                                           unique key uk_user_follow_relation (follower_user_id, followed_user_id),
                                           key idx_user_follow_followed (followed_user_id, created_at),
                                           key idx_user_follow_follower (follower_user_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- ============================================================================
-- growth-module
-- source: growth-module/src/main/resources/db/growth-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS growth_account (
                                              id BIGINT NOT NULL, user_id BIGINT NOT NULL, total_points INT NOT NULL DEFAULT 0,
                                              level_code VARCHAR(16) NOT NULL DEFAULT 'LV1', version INT NOT NULL DEFAULT 0,
                                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                              deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                                              UNIQUE KEY uk_growth_account_user (user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS growth_log (
                                          id BIGINT NOT NULL, user_id BIGINT NOT NULL, biz_type VARCHAR(32) NOT NULL,
                                          biz_id VARCHAR(64) NOT NULL, point_delta INT NOT NULL, balance_after INT NOT NULL,
                                          remark VARCHAR(255) NULL, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                          deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                                          UNIQUE KEY uk_growth_log_biz (biz_type, biz_id, user_id),
                                          KEY idx_growth_log_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS growth_level_rule (
                                                 id BIGINT NOT NULL, level_code VARCHAR(16) NOT NULL, level_name VARCHAR(32) NOT NULL,
                                                 min_points INT NOT NULL, max_points INT NULL, benefit_json JSON NOT NULL,
                                                 enabled_flag TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                 deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                                                 UNIQUE KEY uk_growth_level_code (level_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS growth_badge (
                                            id BIGINT NOT NULL, badge_code VARCHAR(32) NOT NULL, badge_name VARCHAR(64) NOT NULL,
                                            badge_image_key VARCHAR(512) NULL, condition_description VARCHAR(128) NOT NULL,
                                            event_type VARCHAR(32) NOT NULL, threshold INT NOT NULL,
                                            enabled_flag TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                            deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                                            UNIQUE KEY uk_growth_badge_code (badge_code, deleted),
                                            KEY idx_growth_badge_event_threshold (event_type, enabled_flag, deleted, threshold)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS growth_user_badge (
                                                 id BIGINT NOT NULL, user_id BIGINT NOT NULL, badge_id BIGINT NOT NULL,
                                                 source_biz_id VARCHAR(64) NULL, awarded_at DATETIME NOT NULL,
                                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                 deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                                                 UNIQUE KEY uk_growth_user_badge (user_id, badge_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO growth_level_rule (
    id, level_code, level_name, min_points, max_points, benefit_json, enabled_flag, deleted
) VALUES
      (900001, 'LV1', '同路新手', 0, 499, JSON_OBJECT(), 1, 0),
      (900002, 'LV2', '同路行者', 500, 1999, JSON_OBJECT(), 1, 0),
      (900003, 'LV3', '同路先锋', 2000, 4999, JSON_OBJECT(), 1, 0),
      (900004, 'LV4', '同路达人', 5000, 9999, JSON_OBJECT(), 1, 0),
      (900005, 'LV5', '同路大使', 10000, 19999, JSON_OBJECT(), 1, 0),
      (900006, 'LV6', '同路传奇', 20000, NULL, JSON_OBJECT(), 1, 0);

INSERT IGNORE INTO growth_badge (
    id, badge_code, badge_name, badge_image_key, condition_description, event_type, threshold, enabled_flag, deleted
) VALUES
      (910001, 'FIRST_DEPARTURE', '第一次出发', NULL, '完成首次组队行程', 'TEAM_TRIP_COMPLETED', 1, 1, 0),
      (910002, 'DISTANCE_100KM', '百公里', NULL, '累计有效行驶 100 公里', 'TRIP_MILEAGE', 10, 1, 0),
      (910003, 'DISTANCE_1000KM', '千里马', NULL, '累计有效行驶 1000 公里', 'TRIP_MILEAGE', 100, 1, 0),
      (910004, 'DISTANCE_10000KM', '万里行', NULL, '累计有效行驶 10000 公里', 'TRIP_MILEAGE', 1000, 1, 0),
      (910005, 'INVITE_10', '社交达人', NULL, '成功邀请 10 位新用户注册', 'INVITE_USER_REGISTER', 10, 1, 0),
      (910006, 'INVITE_50', '人气王', NULL, '成功邀请 50 位新用户注册', 'INVITE_USER_REGISTER', 50, 1, 0),
      (910007, 'HELP_10', '助人为乐', NULL, '完成 10 次互助', 'HELP_COMPLETED', 10, 1, 0),
      (910008, 'HELP_50', '救急先锋', NULL, '完成 50 次互助', 'HELP_COMPLETED', 50, 1, 0),
      (910009, 'ROUTE_G318', '318勇士', NULL, '完成 G318 川藏线路线', 'ROUTE_G318_COMPLETED', 1, 1, 0),
      (910010, 'FOUR_SEASONS', '四季行者', NULL, '春夏秋冬各完成一次组队', 'FOUR_SEASONS_TRIP', 1, 1, 0);

UPDATE growth_badge SET condition_description = '完成首次组队行程', event_type = 'TEAM_TRIP_COMPLETED', threshold = 1 WHERE badge_code = 'FIRST_DEPARTURE' AND deleted = 0;
UPDATE growth_badge SET condition_description = '累计有效行驶 100 公里', event_type = 'TRIP_MILEAGE', threshold = 10 WHERE badge_code = 'DISTANCE_100KM' AND deleted = 0;
UPDATE growth_badge SET condition_description = '累计有效行驶 1000 公里', event_type = 'TRIP_MILEAGE', threshold = 100 WHERE badge_code = 'DISTANCE_1000KM' AND deleted = 0;
UPDATE growth_badge SET condition_description = '累计有效行驶 10000 公里', event_type = 'TRIP_MILEAGE', threshold = 1000 WHERE badge_code = 'DISTANCE_10000KM' AND deleted = 0;
UPDATE growth_badge SET condition_description = '成功邀请 10 位新用户注册', event_type = 'INVITE_USER_REGISTER', threshold = 10 WHERE badge_code = 'INVITE_10' AND deleted = 0;
UPDATE growth_badge SET condition_description = '成功邀请 50 位新用户注册', event_type = 'INVITE_USER_REGISTER', threshold = 50 WHERE badge_code = 'INVITE_50' AND deleted = 0;
UPDATE growth_badge SET condition_description = '完成 10 次互助' WHERE badge_code = 'HELP_10' AND deleted = 0;
UPDATE growth_badge SET condition_description = '完成 50 次互助' WHERE badge_code = 'HELP_50' AND deleted = 0;
UPDATE growth_badge SET condition_description = '完成 G318 川藏线路线' WHERE badge_code = 'ROUTE_G318' AND deleted = 0;
UPDATE growth_badge SET condition_description = '春夏秋冬各完成一次组队' WHERE badge_code = 'FOUR_SEASONS' AND deleted = 0;

-- ============================================================================
-- invite-module
-- source: invite-module/src/main/resources/db/invite-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS invite_code (
                                           id BIGINT NOT NULL, user_id BIGINT NOT NULL, invite_code VARCHAR(16) NOT NULL,
                                           enabled_flag TINYINT NOT NULL DEFAULT 1, created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                           deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                                           UNIQUE KEY uk_invite_code_user (user_id, deleted),
                                           UNIQUE KEY uk_invite_code_value (invite_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS invite_relation (
                                               id BIGINT NOT NULL, inviter_user_id BIGINT NOT NULL, invitee_user_id BIGINT NOT NULL,
                                               invite_code VARCHAR(16) NULL, relation_status VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',
                                               bind_source VARCHAR(32) NOT NULL DEFAULT 'MANUAL_CODE',
                                               bind_source_value VARCHAR(64) NULL,
                                               request_id VARCHAR(64) NULL,
                                               invitee_registered_at DATETIME NULL,
                                               bound_at DATETIME NOT NULL, first_team_completed_at DATETIME NULL,
                                               created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                               deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
                                               UNIQUE KEY uk_invite_relation_invitee (invitee_user_id, deleted),
                                               UNIQUE KEY uk_invite_relation_request (request_id, deleted),
                                               KEY idx_invite_relation_inviter (inviter_user_id, relation_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS invite_reward_record (
  id BIGINT NOT NULL, relation_id BIGINT NOT NULL, beneficiary_user_id BIGINT NOT NULL,
  inviter_user_id BIGINT NOT NULL, invitee_user_id BIGINT NOT NULL,
  rule_code VARCHAR(64) NOT NULL, reward_rule_code VARCHAR(64) NOT NULL,
  reward_type VARCHAR(32) NOT NULL DEFAULT 'GROWTH_VALUE', reward_value INT NOT NULL DEFAULT 0,
  reward_biz_no VARCHAR(64) NOT NULL, idempotency_key VARCHAR(128) NOT NULL,
  reward_snapshot_json JSON NOT NULL, reward_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  triggered_at DATETIME NOT NULL, failure_reason VARCHAR(255) NULL,
  granted_at DATETIME NULL, issued_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0, PRIMARY KEY (id),
  UNIQUE KEY uk_invite_reward_biz (reward_biz_no, deleted),
  UNIQUE KEY uk_invite_reward_idempotency (idempotency_key, deleted),
  UNIQUE KEY uk_invite_reward_relation_rule (relation_id, rule_code, deleted),
  KEY idx_invite_reward_user (beneficiary_user_id, reward_status, created_at),
  KEY idx_invite_reward_invitee (invitee_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS invite_reward_rule (
  id BIGINT NOT NULL PRIMARY KEY,
  rule_code VARCHAR(64) NOT NULL,
  reward_type VARCHAR(32) NOT NULL DEFAULT 'GROWTH_VALUE',
  reward_value INT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_invite_reward_rule_code (rule_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO invite_reward_rule
(id, rule_code, reward_type, reward_value, status, created_at, updated_at, deleted)
VALUES
(202607270001, 'INVITE_REGISTER_SUCCESS', 'GROWTH_VALUE', 50, 'ENABLED', NOW(), NOW(), 0),
(202607270002, 'INVITEE_FIRST_TEAM_COMPLETED', 'GROWTH_VALUE', 100, 'ENABLED', NOW(), NOW(), 0),
(202607270003, 'INVITE_STAGE_3', 'GROWTH_VALUE', 200, 'ENABLED', NOW(), NOW(), 0),
(202607270004, 'INVITE_STAGE_10', 'GROWTH_VALUE', 500, 'ENABLED', NOW(), NOW(), 0),
(202607270005, 'INVITE_STAGE_30', 'GROWTH_VALUE', 2000, 'ENABLED', NOW(), NOW(), 0),
(202607270006, 'INVITE_STAGE_50', 'GROWTH_VALUE', 5000, 'ENABLED', NOW(), NOW(), 0);

-- ============================================================================
-- coupon-module
-- source: coupon-module/src/main/resources/db/coupon-schema.sql
-- ============================================================================
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

-- ============================================================================
-- vehicle-module
-- source: vehicle-module/src/main/resources/db/vehicle-schema.sql
-- ============================================================================
create table if not exists vehicle_profile (
                                               id bigint primary key,
                                               user_id bigint not null,
                                               plate_no_cipher varchar(256) null,
                                               plate_no_mask varchar(32) null,
                                               brand varchar(64) not null default '',
                                               model varchar(64) not null default '',
                                               vehicle_type varchar(32) not null default '',
                                               color varchar(32) not null default '',
                                               seat_count tinyint not null default 5,
                                               energy_type varchar(32) not null default '',
                                               vehicle_photo_image_key varchar(512) not null default '',
                                               certification_status varchar(32) not null default 'UNSUBMITTED',
                                               is_default tinyint not null default 0,
                                               created_at datetime not null,
                                               updated_at datetime not null,
                                               deleted tinyint not null default 0,
                                               key idx_vehicle_profile_user (user_id, deleted),
                                               key idx_vehicle_profile_default (user_id, is_default, deleted),
                                               key idx_vehicle_profile_status (certification_status)
);

create table if not exists vehicle_certification (
                                                     id bigint primary key,
                                                     vehicle_id bigint not null,
                                                     user_id bigint not null,
                                                     owner_name varchar(64) not null default '',
                                                     plate_no_cipher varchar(256) null,
                                                     plate_no_mask varchar(32) null,
                                                     vehicle_type varchar(32) not null default '',
                                                     vin_cipher varchar(256) null,
                                                     vin_mask varchar(32) null,
                                                     engine_no_cipher varchar(256) null,
                                                     engine_no_mask varchar(32) null,
                                                     register_date date null,
                                                     issue_date date null,
                                                     issuing_authority varchar(128) null,
                                                     license_front_image_key varchar(512) not null,
                                                     license_back_image_key varchar(512) null,
                                                     recognition_source varchar(32) not null default 'MINIPROGRAM_OCR',
                                                     status varchar(32) not null default 'PENDING',
                                                     reject_reason varchar(255) not null default '',
                                                     submitted_at datetime not null,
                                                     reviewed_at datetime null,
                                                     reviewer_id bigint null,
                                                     key idx_vehicle_cert_vehicle (vehicle_id, submitted_at),
                                                     key idx_vehicle_cert_user (user_id),
                                                     key idx_vehicle_cert_status (status),
                                                     key idx_vehicle_cert_plate_status (plate_no_cipher, status)
);

create table if not exists vehicle_certification_image (
                                                           id bigint primary key,
                                                           certification_id bigint not null,
                                                           vehicle_id bigint not null,
                                                           image_type varchar(32) not null,
                                                           image_key varchar(512) not null,
                                                           sort_no int not null default 0,
                                                           created_at datetime not null,
                                                           deleted tinyint not null default 0,
                                                           unique key uk_vehicle_cert_image (certification_id, image_type, sort_no, deleted),
                                                           key idx_vehicle_cert_image_vehicle (vehicle_id),
                                                           key idx_vehicle_cert_image_cert (certification_id)
);

create table if not exists vehicle_audit_log (
                                                 id bigint primary key,
                                                 vehicle_id bigint null,
                                                 user_id bigint not null,
                                                 operation_type varchar(32) not null,
                                                 before_snapshot text null,
                                                 after_snapshot text null,
                                                 remark varchar(255) null,
                                                 created_at datetime not null,
                                                 key idx_vehicle_audit_vehicle (vehicle_id),
                                                 key idx_vehicle_audit_user (user_id),
                                                 key idx_vehicle_audit_type (operation_type)
);

-- ============================================================================
-- trip-module
-- source: trip-module/src/main/resources/db/trip-schema.sql
-- ============================================================================
create table if not exists trip (
                                    id bigint primary key,
                                    trip_number varchar(20) not null,
                                    user_id bigint not null,
                                    vehicle_id bigint not null,
                                    title varchar(128) not null default '',
                                    description varchar(1000) not null default '',
                                    cover_image_key varchar(512) null,
                                    expected_people int null,
                                    start_name varchar(128) not null,
                                    start_lat decimal(10,6) null,
                                    start_lng decimal(10,6) null,
                                    start_location_name varchar(128) not null default '',
                                    start_location_address varchar(255) not null default '',
                                    start_latitude decimal(10,6) null,
                                    start_longitude decimal(10,6) null,
                                    end_name varchar(128) not null,
                                    end_lat decimal(10,6) null,
                                    end_lng decimal(10,6) null,
                                    end_location_name varchar(128) not null default '',
                                    end_location_address varchar(255) not null default '',
                                    end_latitude decimal(10,6) null,
                                    end_longitude decimal(10,6) null,
                                    route_summary varchar(255) null,
                                    route_polyline_key varchar(512) null,
                                    route_distance int null,
                                    route_duration int null,
                                    route_polyline mediumtext null,
                                    waypoints_json text null,
                                    departure_time datetime not null,
                                    estimated_days int null,
                                    total_distance_meters int null,
                                    max_vehicle_count int not null,
                                    joined_vehicle_count int not null default 1,
                                    vehicle_requirements varchar(128) not null default '不限',
                                    budget_description varchar(128) null,
                                    travel_depth varchar(16) not null,
                                    public_flag tinyint(1) not null default 1,
                                    status varchar(20) not null,
                                    remark varchar(255) null,
                                    actual_start_time datetime null,
                                    actual_end_time datetime null,
                                    created_at datetime not null,
                                    updated_at datetime not null,
                                    deleted tinyint(1) not null default 0,
                                    unique key uk_trip_number (trip_number),
                                    key idx_trip_user_status_time (user_id, status, departure_time),
                                    key idx_trip_public_status_time (public_flag, status, departure_time),
                                    key idx_trip_vehicle (vehicle_id)
);


create table if not exists trip_route (
                                          id bigint primary key,
                                          trip_id bigint null,
                                          draft_id bigint null,
                                          route_plan_id bigint null,
                                          origin json not null,
                                          destination json not null,
                                          waypoints json null,
                                          polyline mediumtext null,
                                          plan_distance int null,
                                          plan_duration int null,
                                          provider_type varchar(32) not null,
                                          route_status varchar(16) not null default 'VALID',
                                          created_at datetime not null,
                                          updated_at datetime not null,
                                          deleted tinyint(1) not null default 0,
                                          unique key uk_trip_route_trip (trip_id, deleted),
                                          unique key uk_trip_route_draft (draft_id, deleted)
);

create table if not exists trip_waypoint (
                                             id bigint primary key,
                                             trip_id bigint null,
                                             draft_id bigint null,
                                             seq_no int not null,
                                             place_name varchar(128) not null,
                                             place_address varchar(255) not null default '',
                                             waypoint_type varchar(16) not null default 'REST',
                                             lat decimal(10,6) null,
                                             lng decimal(10,6) null,
                                             stay_minutes int null,
                                             remark varchar(255) not null default '',
                                             created_at datetime not null,
                                             updated_at datetime not null,
                                             deleted tinyint(1) not null default 0,
                                             key idx_waypoint_trip_seq (trip_id, seq_no),
                                             key idx_waypoint_draft_seq (draft_id, seq_no)
);

create table if not exists trip_member_snapshot (
                                                    id bigint primary key,
                                                    trip_id bigint not null,
                                                    user_id bigint not null,
                                                    vehicle_id bigint null,
                                                    member_role varchar(16) not null,
                                                    join_status varchar(20) not null,
                                                    nickname_snapshot varchar(64) null,
                                                    vehicle_snapshot varchar(128) null,
                                                    joined_at datetime null,
                                                    created_at datetime not null,
                                                    updated_at datetime not null,
                                                    key idx_member_trip_status (trip_id, join_status)
);

create table if not exists trip_audit_log (
                                              id bigint primary key,
                                              trip_id bigint not null,
                                              user_id bigint not null,
                                              operation_type varchar(32) not null,
                                              before_json json null,
                                              after_json json null,
                                              remark varchar(255) null,
                                              created_at datetime not null,
                                              key idx_audit_trip_time (trip_id, created_at)
);

create table if not exists trip_draft (
                                          id bigint primary key,
                                          user_id bigint not null,
                                          title varchar(128) not null default '',
                                          description varchar(1000) not null default '',
                                          cover_image_key varchar(512) null,
                                          start_location_json json null,
                                          end_location_json json null,
                                          waypoint_json json not null,
                                          departure_time datetime null,
                                          duration_days int null,
                                          people_count int null,
                                          vehicle_requirements varchar(128) not null default '不限',
                                          budget_description varchar(128) null,
                                          notes varchar(500) null,
                                          remark varchar(255) not null default '',
                                          draft_status varchar(16) not null default 'DRAFT',
                                          published_trip_id bigint null,
                                          publish_idempotency_key varchar(64) null,
                                          created_at datetime not null default current_timestamp,
                                          updated_at datetime not null default current_timestamp on update current_timestamp,
                                          deleted tinyint not null default 0,
                                          key idx_trip_draft_user_status (user_id, draft_status, updated_at),
                                          unique key uk_trip_draft_publish_key (publish_idempotency_key, deleted)
);

-- ============================================================================
-- map-module
-- source: map-module/src/main/resources/db/map-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS map_route_plan (
                                              id BIGINT NOT NULL,
                                              user_id BIGINT NOT NULL,
                                              route_hash VARCHAR(64) NOT NULL,
                                              route_points_json JSON NOT NULL,
                                              route_result_json JSON NULL,
                                              provider_type VARCHAR(32) NOT NULL,
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
                                                       user_id BIGINT NOT NULL,
                                                       keyword VARCHAR(128) NULL,
                                                       selected_name VARCHAR(128) NULL,
                                                       selected_address VARCHAR(255) NULL,
                                                       selected_latitude DECIMAL(10,6) NULL,
                                                       selected_longitude DECIMAL(10,6) NULL,
                                                       scene VARCHAR(32) NOT NULL,
                                                       provider_type VARCHAR(32) NOT NULL,
                                                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                       updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                       deleted TINYINT(1) NOT NULL DEFAULT 0,
                                                       PRIMARY KEY (id),
                                                       KEY idx_map_search_user_scene_time (user_id, scene, deleted, created_at),
                                                       KEY idx_map_search_keyword (keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;



CREATE TABLE IF NOT EXISTS map_geocode_cache (
                                                 id BIGINT NOT NULL,
                                                 location_hash VARCHAR(64) NOT NULL,
                                                 address VARCHAR(255) NULL,
                                                 latitude DECIMAL(10,6) NULL,
                                                 longitude DECIMAL(10,6) NULL,
                                                 geocode_result_json JSON NULL,
                                                 provider_type VARCHAR(32) NOT NULL,
                                                 expire_at DATETIME NULL,
                                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                 deleted TINYINT(1) NOT NULL DEFAULT 0,
                                                 PRIMARY KEY (id),
                                                 UNIQUE KEY uk_map_geocode_hash (location_hash, provider_type, deleted),
                                                 KEY idx_map_geocode_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- team-module
-- source: team-module/src/main/resources/db/team-schema.sql
-- ============================================================================
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

-- ============================================================================
-- chat-module
-- source: chat-module/src/main/resources/db/chat-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS chat_conversation (
                                                 id BIGINT NOT NULL,
                                                 biz_type VARCHAR(32) NOT NULL,
                                                 biz_id BIGINT NOT NULL,
                                                 conversation_name VARCHAR(64) NOT NULL,
                                                 conversation_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                                 provider_type VARCHAR(32) NOT NULL DEFAULT 'MOCK',
                                                 provider_conversation_key VARCHAR(128) NULL,
                                                 last_message_id BIGINT NULL,
                                                 last_message_preview VARCHAR(120) NULL,
                                                 last_message_at DATETIME NULL,
                                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                 deleted TINYINT(1) NOT NULL DEFAULT 0,
                                                 PRIMARY KEY (id),
                                                 UNIQUE KEY uk_chat_biz (biz_type, biz_id, deleted),
                                                 KEY idx_chat_last_time (conversation_status, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_conversation_member (
                                                        id BIGINT NOT NULL,
                                                        conversation_id BIGINT NOT NULL,
                                                        user_id BIGINT NOT NULL,
                                                        member_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
                                                        member_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                                        unread_count INT NOT NULL DEFAULT 0,
                                                        muted_flag TINYINT(1) NOT NULL DEFAULT 0,
                                                        pinned_flag TINYINT(1) NOT NULL DEFAULT 0,
                                                        last_read_message_id BIGINT NULL,
                                                        cleared_before_message_id BIGINT NULL,
                                                        joined_at DATETIME NOT NULL,
                                                        exited_at DATETIME NULL,
                                                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                        deleted TINYINT(1) NOT NULL DEFAULT 0,
                                                        PRIMARY KEY (id),
                                                        UNIQUE KEY uk_chat_member (conversation_id, user_id, deleted),
                                                        KEY idx_chat_member_user (user_id, member_status),
                                                        KEY idx_chat_member_conversation (conversation_id, member_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_join_application (
                                                     id BIGINT NOT NULL,
                                                     conversation_id BIGINT NOT NULL,
                                                     applicant_user_id BIGINT NOT NULL,
                                                     application_message VARCHAR(120) NULL,
                                                     application_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                                     reviewer_user_id BIGINT NULL,
                                                     reviewed_at DATETIME NULL,
                                                     created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                     updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                     deleted TINYINT(1) NOT NULL DEFAULT 0,
                                                     PRIMARY KEY (id),
                                                     KEY idx_chat_join_owner_queue (conversation_id, application_status, created_at),
                                                     KEY idx_chat_join_applicant (applicant_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_message (
                                            id BIGINT NOT NULL,
                                            conversation_id BIGINT NOT NULL,
                                            sender_user_id BIGINT NULL,
                                            message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT',
                                            message_payload_json JSON NOT NULL,
                                            message_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
                                            provider_message_key VARCHAR(128) NULL,
                                            sent_at DATETIME NOT NULL,
                                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                            deleted TINYINT(1) NOT NULL DEFAULT 0,
                                            PRIMARY KEY (id),
                                            KEY idx_chat_msg_conversation_time (conversation_id, sent_at),
                                            KEY idx_chat_msg_sender_time (sender_user_id, sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS message_risk (
                                            id BIGINT NOT NULL,
                                            message_id BIGINT NOT NULL,
                                            risk_level VARCHAR(16) NOT NULL,
                                            risk_type VARCHAR(20) NOT NULL,
                                            confidence INT NOT NULL DEFAULT 0,
                                            status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                            matched_rule VARCHAR(80) NULL,
                                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                            PRIMARY KEY (id),
                                            UNIQUE KEY uk_message_risk_message (message_id),
                                            KEY idx_message_risk_review (status, risk_level, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_group_item (
                                               id BIGINT NOT NULL,
                                               conversation_id BIGINT NOT NULL,
                                               item_type VARCHAR(24) NOT NULL COMMENT 'ANNOUNCEMENT/RESOURCE/POLL/REMINDER',
                                               title VARCHAR(120) NOT NULL,
                                               content VARCHAR(2000) NULL,
                                               payload_json JSON NULL,
                                               item_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
                                               creator_user_id BIGINT NOT NULL,
                                               created_at DATETIME NOT NULL,
                                               updated_at DATETIME NOT NULL,
                                               deleted TINYINT(1) NOT NULL DEFAULT 0,
                                               PRIMARY KEY (id),
                                               KEY idx_chat_group_item (conversation_id,item_type,item_status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群公告、资料、投票与提醒';

CREATE TABLE IF NOT EXISTS chat_group_vote (
                                               id BIGINT NOT NULL,
                                               item_id BIGINT NOT NULL,
                                               option_key VARCHAR(64) NOT NULL,
                                               user_id BIGINT NOT NULL,
                                               created_at DATETIME NOT NULL,
                                               PRIMARY KEY (id),
                                               UNIQUE KEY uk_chat_poll_user (item_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群投票记录';

CREATE TABLE IF NOT EXISTS chat_reminder_dispatch (
                                                      item_id BIGINT NOT NULL,
                                                      dispatched_at DATETIME NOT NULL,
                                                      PRIMARY KEY (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程提醒去重发送记录';

CREATE TABLE IF NOT EXISTS trip_confirmation (
                                                 id BIGINT NOT NULL,
                                                 trip_id BIGINT NOT NULL,
                                                 conversation_id BIGINT NOT NULL,
                                                 creator_user_id BIGINT NOT NULL,
                                                 confirmation_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
                                                 created_at DATETIME NOT NULL,
                                                 closed_at DATETIME NULL,
                                                 PRIMARY KEY (id),
                                                 KEY idx_trip_confirmation (trip_id,confirmation_status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程开始前成员确认批次';

CREATE TABLE IF NOT EXISTS trip_confirm_record (
                                                   id BIGINT NOT NULL,
                                                   confirmation_id BIGINT NOT NULL,
                                                   trip_id BIGINT NOT NULL,
                                                   user_id BIGINT NOT NULL,
                                                   status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
                                                   reject_reason VARCHAR(300) NULL,
                                                   confirm_time DATETIME NULL,
                                                   created_at DATETIME NOT NULL,
                                                   updated_at DATETIME NOT NULL,
                                                   PRIMARY KEY (id),
                                                   UNIQUE KEY uk_trip_confirm_member (confirmation_id,user_id),
                                                   KEY idx_trip_confirm_trip (trip_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程成员参加确认记录';

CREATE TABLE IF NOT EXISTS chat_member_location (
                                                    id BIGINT NOT NULL,
                                                    conversation_id BIGINT NOT NULL,
                                                    user_id BIGINT NOT NULL,
                                                    latitude DECIMAL(10,7) NOT NULL,
                                                    longitude DECIMAL(10,7) NOT NULL,
                                                    speed DECIMAL(8,2) NULL,
                                                    sharing_flag TINYINT(1) NOT NULL DEFAULT 1,
                                                    recorded_at DATETIME NOT NULL,
                                                    updated_at DATETIME NOT NULL,
                                                    PRIMARY KEY (id),
                                                    UNIQUE KEY uk_chat_member_location (conversation_id,user_id),
                                                    KEY idx_chat_location_time (conversation_id,recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程群成员最新共享位置';

CREATE TABLE IF NOT EXISTS chat_report (
                                           id BIGINT NOT NULL,
                                           conversation_id BIGINT NOT NULL,
                                           reporter_user_id BIGINT NOT NULL,
                                           target_type VARCHAR(20) NOT NULL COMMENT 'CONVERSATION/MEMBER/MESSAGE',
                                           target_id VARCHAR(64) NOT NULL,
                                           report_type VARCHAR(32) NOT NULL,
                                           reason VARCHAR(500) NOT NULL,
                                           evidence_json JSON NULL,
                                           report_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                                           reviewer_id BIGINT NULL,
                                           review_note VARCHAR(500) NULL,
                                           reviewed_at DATETIME NULL,
                                           created_at DATETIME NOT NULL,
                                           updated_at DATETIME NOT NULL,
                                           PRIMARY KEY (id),
                                           KEY idx_chat_report_review (report_status,created_at),
                                           KEY idx_chat_report_conversation (conversation_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊、成员与消息举报';

-- ============================================================================
-- match-module
-- source: match-module/src/main/resources/db/match-schema.sql
-- ============================================================================
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

-- ============================================================================
-- storage-module
-- source: storage-module/src/main/resources/db/storage-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS file_storage (
                                            id BIGINT NOT NULL,
                                            bucket VARCHAR(128) NOT NULL,
                                            object_key VARCHAR(512) NOT NULL,
                                            original_file_name VARCHAR(255) NOT NULL,
                                            content_type VARCHAR(128) NOT NULL,
                                            file_size BIGINT NOT NULL,
                                            biz_type VARCHAR(64) NOT NULL,
                                            biz_id VARCHAR(128) NULL,
                                            user_id BIGINT NULL,
                                            storage_type VARCHAR(32) NOT NULL DEFAULT 'MINIO',
                                            upload_status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
                                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                            deleted TINYINT NOT NULL DEFAULT 0,
                                            PRIMARY KEY (id),
                                            UNIQUE KEY uk_file_object (bucket, object_key, deleted),
                                            KEY idx_file_biz (biz_type, biz_id, deleted),
                                            KEY idx_file_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- driver-track-module
-- source: driver-track-module/src/main/resources/db/driver-track-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS driver_track_record (
    id BIGINT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    longitude DECIMAL(10,6) NOT NULL,
    latitude DECIMAL(10,6) NOT NULL,
    altitude DECIMAL(10,2) NULL,
    speed DECIMAL(10,2) NULL,
    direction DECIMAL(10,2) NULL,
    accuracy DECIMAL(10,2) NOT NULL,
    raw_distance_from_prev INT NOT NULL DEFAULT 0,
    distance_from_prev INT NOT NULL DEFAULT 0,
    calculated_speed_kmh DECIMAL(10,2) NOT NULL DEFAULT 0,
    provider VARCHAR(16) NOT NULL DEFAULT 'fused',
    app_state VARCHAR(16) NOT NULL DEFAULT 'foreground',
    battery_level INT NULL,
    device_id VARCHAR(128) NULL,
    sequence_no BIGINT NOT NULL,
    mock_location TINYINT NOT NULL DEFAULT 0,
    point_status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED',
    valid_point TINYINT NOT NULL DEFAULT 1,
    risk_score INT NOT NULL DEFAULT 0,
    risk_flags VARCHAR(255) NULL,
    reject_reason VARCHAR(255) NULL,
    record_time DATETIME NOT NULL,
    client_send_time DATETIME NULL,
    server_receive_time DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_driver_track_sequence (trip_id, driver_id, sequence_no, deleted),
    KEY idx_driver_track_trip_time (trip_id, record_time),
    KEY idx_driver_track_driver_time (driver_id, record_time),
    KEY idx_driver_track_status (trip_id, point_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS driver_track_distance_record (
    id BIGINT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    total_distance INT NOT NULL DEFAULT 0,
    last_settle_distance INT NOT NULL DEFAULT 0,
    settle_type VARCHAR(32) NOT NULL,
    settle_key VARCHAR(128) NOT NULL,
    settle_time DATETIME NOT NULL,
    event_published TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_driver_track_distance_settle_key (settle_key, deleted),
    KEY idx_driver_track_distance_trip_driver (trip_id, driver_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS driver_track_deviation_record (
    id BIGINT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    driver_id BIGINT NOT NULL,
    longitude DECIMAL(10,6) NOT NULL,
    latitude DECIMAL(10,6) NOT NULL,
    deviation_distance INT NOT NULL DEFAULT 0,
    deviation_status TINYINT NOT NULL DEFAULT 0,
    record_time DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_driver_track_deviation_trip_driver_time (trip_id, driver_id, record_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_track_summary (
    id BIGINT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    primary_user_id BIGINT NOT NULL,
    raw_distance_meters INT NOT NULL DEFAULT 0,
    filtered_distance_meters INT NOT NULL DEFAULT 0,
    approved_distance_meters INT NOT NULL DEFAULT 0,
    total_point_count INT NOT NULL DEFAULT 0,
    valid_point_count INT NOT NULL DEFAULT 0,
    invalid_point_count INT NOT NULL DEFAULT 0,
    location_gap_count INT NOT NULL DEFAULT 0,
    warning_count INT NOT NULL DEFAULT 0,
    hard_anomaly_count INT NOT NULL DEFAULT 0,
    risk_score INT NOT NULL DEFAULT 0,
    risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW',
    settlement_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    review_reason VARCHAR(255) NULL,
    reviewer_id BIGINT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_trip_track_summary_trip (trip_id, deleted),
    KEY idx_trip_track_summary_risk (risk_level, settlement_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_track_anomaly (
    id BIGINT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    previous_point_id BIGINT NULL,
    current_point_id BIGINT NULL,
    anomaly_type VARCHAR(64) NOT NULL,
    risk_score INT NOT NULL DEFAULT 0,
    detail_json JSON NULL,
    occurred_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    KEY idx_trip_track_anomaly_trip_time (trip_id, occurred_at),
    KEY idx_trip_track_anomaly_user_time (user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_member_distance_alert (
    id BIGINT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    captain_user_id BIGINT NOT NULL,
    member_user_id BIGINT NOT NULL,
    alert_level VARCHAR(24) NOT NULL,
    distance_m INT NOT NULL,
    started_at DATETIME NOT NULL,
    notified_at DATETIME NULL,
    recovered_at DATETIME NULL,
    acknowledged_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_trip_member_alert_active (trip_id, member_user_id, recovered_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_execution (
    id BIGINT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    captain_user_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    planned_distance_m INT NOT NULL DEFAULT 0,
    raw_gps_distance_m INT NOT NULL DEFAULT 0,
    matched_road_distance_m INT NOT NULL DEFAULT 0,
    estimated_gap_distance_m INT NOT NULL DEFAULT 0,
    settlement_distance_m INT NOT NULL DEFAULT 0,
    started_at DATETIME NULL,
    ended_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_trip_execution_trip (trip_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_execution_member (
    id BIGINT PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    trip_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    member_role VARCHAR(24) NOT NULL,
    member_status VARCHAR(32) NOT NULL,
    ready_at DATETIME NULL,
    joined_execution_at DATETIME NULL,
    left_at DATETIME NULL,
    eligible_flag TINYINT NOT NULL DEFAULT 0,
    ineligible_reason VARCHAR(128) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_execution_member (execution_id, user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_track_point (
    id BIGINT PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    trip_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    device_id VARCHAR(128) NULL,
    sequence_no BIGINT NULL,
    longitude DECIMAL(10,6) NOT NULL,
    latitude DECIMAL(10,6) NOT NULL,
    altitude DECIMAL(10,2) NULL,
    accuracy DECIMAL(10,2) NULL,
    speed DECIMAL(10,2) NULL,
    bearing DECIMAL(10,2) NULL,
    provider VARCHAR(16) NOT NULL DEFAULT 'fused',
    app_state VARCHAR(16) NOT NULL DEFAULT 'foreground',
    battery_level INT NULL,
    located_at DATETIME NOT NULL,
    client_send_time DATETIME NULL,
    server_receive_time DATETIME NULL,
    mock_location TINYINT NOT NULL DEFAULT 0,
    point_status VARCHAR(32) NOT NULL,
    valid_point TINYINT NOT NULL DEFAULT 1,
    risk_score INT NOT NULL DEFAULT 0,
    risk_flags VARCHAR(255) NULL,
    reject_reason VARCHAR(255) NULL,
    calculated_speed_kmh DECIMAL(10,2) NOT NULL DEFAULT 0,
    raw_distance_from_previous_m INT NOT NULL DEFAULT 0,
    distance_from_previous_m INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_track_device_sequence (execution_id, user_id, device_id, sequence_no),
    KEY idx_track_execution_time (execution_id, located_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_route_plan_version (
    id BIGINT NOT NULL PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    trip_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    route_polyline LONGTEXT NOT NULL,
    planned_distance_m INT NOT NULL DEFAULT 0,
    required_waypoints_json JSON NULL,
    effective_at DATETIME NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_execution_route_version (execution_id, version_no, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_waypoint_arrival (
    id BIGINT PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    trip_id BIGINT NOT NULL,
    waypoint_id BIGINT NULL,
    arrival_type VARCHAR(24) NOT NULL DEFAULT 'WAYPOINT',
    user_id BIGINT NOT NULL,
    first_inside_at DATETIME NOT NULL,
    confirmed_at DATETIME NOT NULL,
    evidence_point_count INT NOT NULL,
    distance_m INT NOT NULL,
    created_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_execution_waypoint_arrival (execution_id, waypoint_id, arrival_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_track_source_switch (
    id BIGINT NOT NULL PRIMARY KEY,
    execution_id BIGINT NOT NULL,
    from_user_id BIGINT NULL,
    to_user_id BIGINT NOT NULL,
    switch_reason VARCHAR(64) NOT NULL,
    switched_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    KEY idx_track_source_execution (execution_id, switched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trip_mileage_settlement (
    id BIGINT PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    raw_gps_distance_m INT NOT NULL DEFAULT 0,
    matched_road_distance_m INT NOT NULL DEFAULT 0,
    estimated_gap_distance_m INT NOT NULL DEFAULT 0,
    settlement_distance_m INT NOT NULL DEFAULT 0,
    track_coverage_rate INT NOT NULL DEFAULT 0,
    estimated_ratio INT NOT NULL DEFAULT 0,
    quality_status VARCHAR(32) NOT NULL,
    settlement_status VARCHAR(32) NOT NULL,
    growth_value INT NOT NULL DEFAULT 0,
    reason VARCHAR(255) NULL,
    settled_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_trip_mileage_settlement (trip_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ============================================================================
-- merchant-module
-- source: merchant-module/src/main/resources/db/merchant-schema.sql
-- ============================================================================
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

-- 普通商户升级为平台合作商的独立申请，不与首次商户认证混用。
CREATE TABLE IF NOT EXISTS merchant_partner_application (
                                                            merchant_id BIGINT NOT NULL,
                                                            application_reason VARCHAR(500) NOT NULL,
                                                            cooperation_categories VARCHAR(255) NOT NULL DEFAULT '',
                                                            planned_monthly_stock INT NOT NULL DEFAULT 0,
                                                            application_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                                                            reject_reason VARCHAR(255) NOT NULL DEFAULT '',
                                                            reviewer_id BIGINT NULL,
                                                            reviewed_at DATETIME NULL,
                                                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                            deleted TINYINT NOT NULL DEFAULT 0,
                                                            PRIMARY KEY (merchant_id),
                                                            KEY idx_partner_application_status (application_status, updated_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS merchant_partner_cancellation (
                                                             merchant_id BIGINT NOT NULL,
                                                             cancellation_reason VARCHAR(500) NOT NULL,
                                                             cancellation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                                                             reject_reason VARCHAR(255) NOT NULL DEFAULT '',
                                                             reviewer_id BIGINT NULL,
                                                             requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                             reviewed_at DATETIME NULL,
                                                             updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                             deleted TINYINT NOT NULL DEFAULT 0,
                                                             PRIMARY KEY (merchant_id),
                                                             KEY idx_partner_cancellation_status (cancellation_status, updated_at, deleted)
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

-- ============================================================================
-- groupbuy-module
-- source: groupbuy-module/src/main/resources/db/groupbuy-schema.sql
-- ============================================================================
create table if not exists groupbuy_activity (
                                                 id bigint not null,
                                                 merchant_id bigint not null default 0,
                                                 product_id bigint not null,
                                                 initiator_user_id bigint not null,
                                                 target_people int not null,
                                                 current_people int not null default 0,
                                                 group_price decimal(12,2) not null default 0.00,
                                                 ladder_price_json text null,
                                                 activity_status varchar(32) not null,
                                                 start_at datetime not null,
                                                 expire_at datetime not null,
                                                 success_at datetime null,
                                                 failed_at datetime null,
                                                 created_at datetime not null,
                                                 updated_at datetime not null,
                                                 deleted tinyint not null default 0,
                                                 primary key (id),
                                                 key idx_groupbuy_activity_product (product_id, activity_status),
                                                 key idx_groupbuy_activity_merchant (merchant_id, activity_status),
                                                 key idx_groupbuy_activity_expire (activity_status, expire_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists groupbuy_participant (
                                                    id bigint not null,
                                                    activity_id bigint not null,
                                                    order_id bigint not null,
                                                    user_id bigint not null,
                                                    participant_status varchar(32) not null,
                                                    joined_at datetime not null,
                                                    paid_at datetime null,
                                                    refunded_at datetime null,
                                                    created_at datetime not null,
                                                    updated_at datetime not null,
                                                    deleted tinyint not null default 0,
                                                    primary key (id),
                                                    unique key uk_groupbuy_participant_user (activity_id, user_id, deleted),
                                                    key idx_groupbuy_participant_order (order_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- ============================================================================
-- order-module
-- source: order-module/src/main/resources/db/order-schema.sql
-- ============================================================================
create table if not exists order_trade (
                                           id bigint not null,
                                           order_no varchar(64) not null,
                                           user_id bigint not null,
                                           merchant_id bigint not null default 0,
                                           product_id bigint not null,
                                           activity_id bigint null,
                                           original_amount decimal(12,2) not null default 0.00,
                                           groupbuy_discount_amount decimal(12,2) not null default 0.00,
                                           coupon_deduction_amount decimal(12,2) not null default 0.00,
                                           payable_amount decimal(12,2) not null default 0.00,
                                           paid_amount decimal(12,2) not null default 0.00,
                                           user_coupon_id bigint null,
                                           order_status varchar(32) not null,
                                           payment_status varchar(32) not null,
                                           verification_status varchar(32) not null,
                                           refund_status varchar(32) not null,
                                           profit_sharing_status varchar(32) not null,
                                           expire_at datetime null,
                                           paid_at datetime null,
                                           completed_at datetime null,
                                           remark varchar(255) not null default '',
                                           created_at datetime not null,
                                           updated_at datetime not null,
                                           deleted tinyint not null default 0,
                                           primary key (id),
                                           unique key uk_order_trade_no (order_no),
                                           key idx_order_trade_user (user_id, created_at),
                                           key idx_order_trade_merchant (merchant_id, created_at),
                                           key idx_order_trade_status (order_status, payment_status),
                                           key idx_order_trade_activity (activity_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists order_item (
                                          id bigint not null,
                                          order_id bigint not null,
                                          product_id bigint not null,
                                          product_name varchar(128) not null,
                                          product_type varchar(32) not null,
                                          unit_price decimal(12,2) not null default 0.00,
                                          quantity int not null default 1,
                                          total_amount decimal(12,2) not null default 0.00,
                                          snapshot_json text null,
                                          created_at datetime not null,
                                          primary key (id),
                                          key idx_order_item_order (order_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists order_compensation_task (
                                                       id bigint not null,
                                                       biz_type varchar(64) not null,
                                                       biz_id varchar(64) not null,
                                                       idempotent_key varchar(128) not null,
                                                       target_module varchar(64) not null,
                                                       request_payload text null,
                                                       task_status varchar(32) not null,
                                                       retry_count int not null default 0,
                                                       next_retry_at datetime null,
                                                       last_error varchar(1000) null,
                                                       created_at datetime not null,
                                                       updated_at datetime not null,
                                                       primary key (id),
                                                       key idx_order_compensation_status (task_status, next_retry_at),
                                                       key idx_order_compensation_biz (biz_type, biz_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- ============================================================================
-- payment-module
-- source: payment-module/src/main/resources/db/payment-schema.sql
-- ============================================================================
create table if not exists payment_record (
                                              id bigint not null,
                                              order_id bigint not null,
                                              order_no varchar(64) not null default '',
                                              payment_no varchar(64) not null,
                                              wx_prepay_id varchar(128) null,
                                              wx_transaction_id varchar(128) null,
                                              pay_channel varchar(32) not null,
                                              pay_amount decimal(12,2) not null default 0.00,
                                              payment_status varchar(32) not null,
                                              callback_payload text null,
                                              paid_at datetime null,
                                              created_at datetime not null,
                                              updated_at datetime not null,
                                              deleted tinyint not null default 0,
                                              primary key (id),
                                              unique key uk_payment_no (payment_no),
                                              unique key uk_payment_wx_transaction (wx_transaction_id),
                                              key idx_payment_order (order_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists payment_refund_record (
                                                     id bigint not null,
                                                     order_id bigint not null,
                                                     refund_no varchar(64) not null,
                                                     wx_refund_id varchar(128) null,
                                                     user_id bigint not null,
                                                     refund_amount decimal(12,2) not null default 0.00,
                                                     refund_reason varchar(255) not null default '',
                                                     refund_type varchar(32) not null,
                                                     refund_status varchar(32) not null,
                                                     audit_status varchar(32) not null,
                                                     callback_payload text null,
                                                     requested_at datetime not null,
                                                     refunded_at datetime null,
                                                     created_at datetime not null,
                                                     updated_at datetime not null,
                                                     deleted tinyint not null default 0,
                                                     primary key (id),
                                                     unique key uk_payment_refund_no (refund_no),
                                                     key idx_payment_refund_order (order_id),
                                                     key idx_payment_refund_status (refund_status, audit_status)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists payment_profit_sharing_record (
                                                             id bigint not null,
                                                             order_id bigint not null,
                                                             merchant_id bigint not null,
                                                             verification_id bigint not null,
                                                             sharing_no varchar(64) not null,
                                                             wx_sharing_id varchar(128) null,
                                                             total_amount decimal(12,2) not null default 0.00,
                                                             platform_commission_amount decimal(12,2) not null default 0.00,
                                                             merchant_amount decimal(12,2) not null default 0.00,
                                                             commission_rate decimal(5,4) not null default 0.0000,
                                                             sharing_status varchar(32) not null,
                                                             callback_payload text null,
                                                             shared_at datetime null,
                                                             created_at datetime not null,
                                                             updated_at datetime not null,
                                                             deleted tinyint not null default 0,
                                                             primary key (id),
                                                             unique key uk_payment_sharing_no (sharing_no),
                                                             key idx_payment_sharing_order (order_id),
                                                             key idx_payment_sharing_merchant (merchant_id, created_at),
                                                             key idx_payment_sharing_status (sharing_status)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- ============================================================================
-- verification-module
-- source: verification-module/src/main/resources/db/verification-schema.sql
-- ============================================================================
create table if not exists verification_code
(
    id                bigint         not null comment '核销码ID',
    verification_code varchar(128)   not null comment '核销码',
    qr_content        varchar(512)   not null comment '二维码内容',
    biz_type          varchar(32)    not null comment '业务类型：ORDER、COUPON',
    biz_id            bigint         not null comment '业务ID',
    order_id          bigint         null comment '订单ID',
    user_coupon_id    bigint         null comment '用户券ID',
    user_id           bigint         not null comment '用户ID',
    merchant_id       bigint         not null comment '商家ID',
    amount            decimal(12, 2) not null default 0.00 comment '核销金额',
    code_status       varchar(32)    not null comment '核销码状态',
    expire_at         datetime       not null comment '过期时间',
    verified_at       datetime       null comment '核销时间',
    created_at        datetime       not null comment '创建时间',
    updated_at        datetime       not null comment '更新时间',
    deleted           tinyint        not null default 0 comment '逻辑删除',
    primary key (id),
    unique key uk_verification_code (verification_code),
    unique key uk_verification_biz (biz_type, biz_id, deleted),
    key idx_verification_code_merchant (merchant_id, code_status),
    key idx_verification_code_expire (code_status, expire_at)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
    comment = '核销码表';

create table if not exists verification_record
(
    id                    bigint         not null comment '核销记录ID',
    verification_code_id  bigint         not null comment '核销码ID',
    verification_code     varchar(128)   not null comment '核销码快照',
    biz_type              varchar(32)    not null comment '业务类型',
    biz_id                bigint         not null comment '业务ID',
    order_id              bigint         null comment '订单ID',
    user_coupon_id        bigint         null comment '用户券ID',
    user_id               bigint         not null comment '用户ID',
    merchant_id           bigint         not null comment '商家ID',
    operator_id           bigint         not null comment '操作员ID',
    amount                decimal(12, 2) not null default 0.00 comment '核销金额',
    verification_status   varchar(32)    not null comment '核销状态',
    location_name         varchar(128)   null comment '核销地点',
    longitude             decimal(10, 6) null comment '经度',
    latitude              decimal(10, 6) null comment '纬度',
    verified_at           datetime       not null comment '核销时间',
    reversal_status       varchar(32)    not null default 'NONE' comment '撤销状态',
    created_at            datetime       not null comment '创建时间',
    updated_at            datetime       not null comment '更新时间',
    deleted               tinyint        not null default 0 comment '逻辑删除',
    primary key (id),
    unique key uk_verification_record_code (verification_code_id),
    key idx_verification_record_merchant (merchant_id, verified_at),
    key idx_verification_record_user (user_id, verified_at),
    key idx_verification_record_biz (biz_type, biz_id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
    comment = '核销记录表';

create table if not exists verification_reversal_request
(
    id              bigint       not null comment '申请ID',
    verification_id bigint       not null comment '核销记录ID',
    merchant_id     bigint       not null comment '商家ID',
    applicant_id    bigint       not null comment '申请人ID',
    reason          varchar(255) not null comment '撤销原因',
    audit_status    varchar(32)  not null comment '审核状态',
    reviewer_id     bigint       null comment '审核人ID',
    reviewed_at     datetime     null comment '审核时间',
    reject_reason   varchar(255) null comment '驳回原因',
    created_at      datetime     not null comment '创建时间',
    updated_at      datetime     not null comment '更新时间',
    deleted         tinyint      not null default 0 comment '逻辑删除',
    primary key (id),
    key idx_verification_reversal_record (verification_id),
    key idx_verification_reversal_status (audit_status, created_at)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
    comment = '核销撤销申请表';

create table if not exists verification_compensation_task
(
    id              bigint        not null comment '任务ID',
    biz_type        varchar(64)   not null comment '业务类型',
    biz_id          varchar(64)   not null comment '业务ID',
    idempotent_key  varchar(128)  not null comment '幂等键',
    target_module   varchar(64)   not null comment '目标模块',
    request_payload text          not null comment '请求报文',
    task_status     varchar(32)   not null comment '任务状态',
    retry_count     int           not null default 0 comment '重试次数',
    next_retry_at   datetime      null comment '下次重试时间',
    last_error      varchar(1000) null comment '最近错误',
    created_at      datetime      not null comment '创建时间',
    updated_at      datetime      not null comment '更新时间',
    primary key (id),
    unique key uk_verification_compensation_idem (idempotent_key),
    key idx_verification_compensation_retry (task_status, next_retry_at),
    key idx_verification_compensation_biz (biz_type, biz_id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
    comment = '核销跨模块补偿任务表';

-- ============================================================================
-- assessment-module
-- source: assessment-module/src/main/resources/db/assessment-schema.sql
-- ============================================================================
create table if not exists assessment_level_mapping (
                                                        id bigint not null,
                                                        level_code varchar(16) not null,
                                                        min_score decimal(8,2) not null,
                                                        max_score decimal(8,2) not null,
                                                        commission_rate decimal(8,4) not null,
                                                        rank_weight decimal(8,4) not null,
                                                        exclusion_radius_km decimal(8,2) not null default 0.00,
                                                        mapping_status varchar(32) not null default 'ACTIVE',
                                                        created_at datetime not null,
                                                        updated_at datetime not null,
                                                        deleted tinyint not null default 0,
                                                        primary key (id),
                                                        unique key uk_assessment_level (level_code, deleted),
                                                        key idx_assessment_score_range (min_score, max_score, mapping_status)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists assessment_merchant_score (
                                                         id bigint not null,
                                                         merchant_id bigint not null,
                                                         assessment_period varchar(16) not null,
                                                         total_score decimal(8,2) not null,
                                                         merchant_level varchar(16) not null,
                                                         commission_rate decimal(8,4) not null,
                                                         rank_weight decimal(8,4) not null,
                                                         exclusion_radius_km decimal(8,2) not null default 0.00,
                                                         calculate_status varchar(32) not null,
                                                         calculated_at datetime not null,
                                                         request_id varchar(128) not null,
                                                         created_at datetime not null,
                                                         updated_at datetime not null,
                                                         deleted tinyint not null default 0,
                                                         primary key (id),
                                                         unique key uk_assessment_merchant_assessment_period (merchant_id, assessment_period, deleted),
                                                         unique key uk_assessment_request (request_id, deleted),
                                                         key idx_assessment_merchant_latest (merchant_id, calculated_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists assessment_merchant_score_item (
                                                              id bigint not null,
                                                              score_id bigint not null,
                                                              metric_key varchar(64) not null,
                                                              metric_value decimal(12,2) not null,
                                                              score_delta decimal(8,2) not null,
                                                              created_at datetime not null,
                                                              deleted tinyint not null default 0,
                                                              primary key (id),
                                                              key idx_assessment_item_score (score_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;


create table if not exists assessment_manual_adjustment (
                                                            id bigint not null,
                                                            merchant_id bigint not null,
                                                            score_delta decimal(8,2) not null,
                                                            reason varchar(255) not null,
                                                            operator_id bigint not null,
                                                            request_id varchar(128) not null,
                                                            created_at datetime not null,
                                                            updated_at datetime not null,
                                                            deleted tinyint not null default 0,
                                                            primary key (id),
                                                            unique key uk_assessment_manual_adjustment_request (request_id, deleted),
                                                            key idx_assessment_manual_adjustment_merchant (merchant_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- ============================================================================
-- customer-service-module
-- source: customer-service-module/src/main/resources/db/customer-service-schema.sql
-- ============================================================================
create table if not exists customer_service_ticket (
                                                       id bigint not null,
                                                       creator_type varchar(32) not null,
                                                       creator_id bigint not null,
                                                       scene varchar(64) not null,
                                                       target_type varchar(64) not null default '',
                                                       target_id varchar(64) not null default '',
                                                       title varchar(128) not null,
                                                       content varchar(2048) not null,
                                                       ticket_status varchar(32) not null default 'OPEN',
                                                       priority varchar(32) not null default 'NORMAL',
                                                       assigned_admin_id bigint null,
                                                       request_id varchar(128) not null,
                                                       created_at datetime not null,
                                                       updated_at datetime not null,
                                                       closed_at datetime null,
                                                       deleted tinyint not null default 0,
                                                       primary key (id),
                                                       unique key uk_customer_service_ticket_request (request_id, deleted),
                                                       key idx_customer_service_creator (creator_type, creator_id, created_at),
                                                       key idx_customer_service_status (ticket_status, priority, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists customer_service_ticket_message (
                                                               id bigint not null,
                                                               ticket_id bigint not null,
                                                               sender_type varchar(32) not null,
                                                               sender_id bigint not null,
                                                               message_type varchar(32) not null,
                                                               content varchar(2048) not null,
                                                               image_keys_json text null,
                                                               request_id varchar(128) null,
                                                               created_at datetime not null,
                                                               deleted tinyint not null default 0,
                                                               primary key (id),
                                                               key idx_customer_service_message_ticket (ticket_id, created_at),
                                                               unique key uk_customer_service_message_request (request_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- ============================================================================
-- notify-module
-- source: notify-module/src/main/resources/db/notify-schema.sql
-- ============================================================================
create table if not exists notify_message (
                                              id bigint not null,
                                              receiver_type varchar(32) not null,
                                              receiver_id bigint not null,
                                              scene varchar(64) not null default 'SYSTEM',
                                              event_type varchar(64) not null,
                                              title varchar(128) not null,
                                              content varchar(1024) not null,
                                              target_type varchar(64) not null default '',
                                              target_id varchar(64) not null default '',
                                              read_status varchar(32) not null default 'UNREAD',
                                              read_at datetime null,
                                              request_id varchar(128) not null,
                                              created_at datetime not null,
                                              updated_at datetime not null,
                                              deleted tinyint not null default 0,
                                              primary key (id),
                                              unique key uk_notify_message_request (request_id, deleted),
                                              key idx_notify_receiver (receiver_type, receiver_id, read_status, created_at),
                                              key idx_notify_target (target_type, target_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists notify_template (
                                               id bigint not null,
                                               template_code varchar(64) not null,
                                               channel varchar(32) not null,
                                               title_template varchar(128) not null,
                                               content_template varchar(1024) not null,
                                               template_status varchar(32) not null default 'ACTIVE',
                                               created_at datetime not null,
                                               updated_at datetime not null,
                                               deleted tinyint not null default 0,
                                               primary key (id),
                                               unique key uk_notify_template_code (template_code, channel, deleted)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists notify_delivery_log (
                                                   id bigint not null,
                                                   message_id bigint not null,
                                                   channel varchar(32) not null,
                                                   delivery_status varchar(32) not null,
                                                   third_request_id varchar(128) not null default '',
                                                   error_message varchar(512) not null default '',
                                                   retry_count int not null default 0,
                                                   next_retry_at datetime null,
                                                   created_at datetime not null,
                                                   updated_at datetime not null,
                                                   deleted tinyint not null default 0,
                                                   primary key (id),
                                                   key idx_notify_delivery_message (message_id, channel),
                                                   key idx_notify_delivery_retry (delivery_status, next_retry_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

-- ============================================================================
-- admin-module
-- source: admin-module/src/main/resources/db/admin-schema.sql
-- ============================================================================
create table if not exists admin_operator
(
    id              bigint       not null comment '运营人员ID',
    username        varchar(64)  not null comment '登录名',
    display_name    varchar(64)  not null comment '展示名',
    phone           varchar(32)  null comment '手机号',
    password_hash   varchar(255) not null comment '密码哈希',
    operator_status varchar(32)  not null comment '状态',
    last_login_at   datetime     null comment '最近登录时间',
    created_at      datetime     not null comment '创建时间',
    updated_at      datetime     not null comment '更新时间',
    deleted         tinyint      not null default 0 comment '逻辑删除',
    primary key (id),
    unique key uk_admin_operator_username (username),
    unique key uk_admin_operator_phone (phone),
    key idx_admin_operator_status (operator_status)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
    comment = '运营人员账号表';

create table if not exists admin_role
(
    id              bigint      not null comment '角色ID',
    role_code       varchar(64) not null comment '角色编码',
    role_name       varchar(64) not null comment '角色名称',
    permission_json text        null comment '权限配置JSON',
    role_status     varchar(32) not null comment '角色状态',
    created_at      datetime    not null comment '创建时间',
    updated_at      datetime    not null comment '更新时间',
    deleted         tinyint     not null default 0 comment '逻辑删除',
    primary key (id),
    unique key uk_admin_role_code (role_code),
    key idx_admin_role_status (role_status)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
    comment = '运营角色表';

create table if not exists admin_operator_role
(
    id          bigint   not null comment '关系ID',
    operator_id bigint   not null comment '运营人员ID',
    role_id     bigint   not null comment '角色ID',
    created_at  datetime not null comment '创建时间',
    deleted     tinyint  not null default 0 comment '逻辑删除',
    primary key (id),
    unique key uk_admin_operator_role (operator_id, role_id, deleted),
    key idx_admin_operator_role_operator (operator_id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
    comment = '运营人员角色关系表';

create table if not exists admin_audit_log
(
    id               bigint       not null comment '日志ID',
    operator_id      bigint       not null comment '操作人ID',
    operator_name    varchar(64)  not null comment '操作人名称快照',
    action_type      varchar(64)  not null comment '操作类型',
    target_module    varchar(64)  not null comment '目标模块',
    target_type      varchar(64)  not null comment '目标类型',
    target_id        varchar(64)  not null comment '目标ID',
    request_id       varchar(128) not null comment '幂等请求号',
    before_snapshot  text         null comment '操作前快照',
    after_snapshot   text         null comment '操作后快照',
    operation_reason varchar(255) null comment '操作原因',
    operation_result varchar(32)  not null comment '操作结果',
    ip               varchar(64)  null comment '操作IP',
    user_agent       varchar(512) null comment 'UA',
    created_at       datetime     not null comment '创建时间',
    primary key (id),
    unique key uk_admin_audit_request (request_id),
    key idx_admin_audit_operator (operator_id, created_at),
    key idx_admin_audit_target (target_module, target_type, target_id),
    key idx_admin_audit_action (action_type, created_at)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
    comment = '运营审计日志表';

create table if not exists admin_operation_config
(
    id              bigint       not null comment '配置ID',
    config_domain   varchar(64)  not null comment '配置域',
    config_key      varchar(128) not null comment '配置键',
    current_version int          not null default 0 comment '当前版本号',
    config_status   varchar(32)  not null comment '配置状态',
    effective_at    datetime     not null comment '生效时间',
    created_at      datetime     not null comment '创建时间',
    updated_at      datetime     not null comment '更新时间',
    deleted         tinyint      not null default 0 comment '逻辑删除',
    primary key (id),
    unique key uk_admin_config_key (config_domain, config_key, deleted),
    key idx_admin_config_status (config_domain, config_status)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
    comment = '运营配置表';

create table if not exists admin_operation_config_version
(
    id            bigint       not null comment '版本ID',
    config_id     bigint       not null comment '配置ID',
    config_domain varchar(64)  not null comment '配置域',
    config_key    varchar(128) not null comment '配置键',
    version_no    int          not null comment '版本号',
    config_value  text         not null comment '配置值JSON',
    effective_at  datetime     not null comment '生效时间',
    operator_id   bigint       not null comment '操作人ID',
    change_reason varchar(255) null comment '修改原因',
    created_at    datetime     not null comment '创建时间',
    primary key (id),
    unique key uk_admin_config_version (config_id, version_no),
    key idx_admin_config_version_effective (config_domain, config_key, effective_at)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
    comment = '运营配置版本表';

create table if not exists admin_compensation_task
(
    id              bigint        not null comment '任务ID',
    biz_type        varchar(64)   not null comment '业务类型',
    biz_id          varchar(64)   not null comment '业务ID',
    idempotent_key  varchar(128)  not null comment '幂等键',
    target_module   varchar(64)   not null comment '目标模块',
    request_payload text          not null comment '请求报文',
    task_status     varchar(32)   not null comment '任务状态',
    retry_count     int           not null default 0 comment '重试次数',
    next_retry_at   datetime      null comment '下次重试时间',
    last_error      varchar(1000) null comment '最近错误',
    created_at      datetime      not null comment '创建时间',
    updated_at      datetime      not null comment '更新时间',
    primary key (id),
    unique key uk_admin_compensation_idem (idempotent_key),
    key idx_admin_compensation_retry (task_status, next_retry_at),
    key idx_admin_compensation_biz (biz_type, biz_id)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
    comment = '运营后台跨模块补偿任务表';

create table if not exists sos_event
(
    id                       bigint         not null comment 'SOS事件ID',
    user_id                  bigint         not null comment '发起用户ID',
    request_id               varchar(128)   not null comment '客户端幂等请求号',
    latitude                 decimal(10, 7) not null comment 'WGS/GCJ定位纬度',
    longitude                decimal(10, 7) not null comment 'WGS/GCJ定位经度',
    location_accuracy_meters decimal(10, 2) null comment '定位精度（米）',
    address                  varchar(255)   not null comment '位置描述',
    message                  varchar(500)   null comment '求助说明',
    alarm_mode               varchar(32)    not null default 'MOCK' comment '外部报警模式，MVP固定MOCK',
    event_status             varchar(32)    not null comment 'PENDING/PROCESSING/RESOLVED/CANCELLED',
    accepted_by              bigint         null comment '受理运营人员ID',
    accepted_at              datetime       null comment '受理时间',
    resolved_by              bigint         null comment '结案运营人员ID',
    resolved_at              datetime       null comment '结案时间',
    resolution_note          varchar(500)   null comment '结案说明',
    occurred_at              datetime       not null comment '用户确认发生时间',
    created_at               datetime       not null comment '创建时间',
    updated_at               datetime       not null comment '更新时间',
    deleted                  tinyint        not null default 0 comment '逻辑删除',
    primary key (id),
    unique key uk_sos_user_request (user_id, request_id),
    key idx_sos_status_time (event_status, occurred_at),
    key idx_sos_user_time (user_id, occurred_at)
) engine = InnoDB
  default charset = utf8mb4
  collate = utf8mb4_unicode_ci
    comment = '用户SOS平台内上报与运营处置表';

SET FOREIGN_KEY_CHECKS = 1;
-- 建表完成。
