-- 同路行数据库：创建数据库并全量重建全部业务表
-- 适用：MySQL 8.0+
--
-- 警告：
--   1. 本脚本会创建并切换到 tongluxing 数据库。
--   2. 本脚本会永久删除 tongluxing 中下方列出的全部业务表及其数据。
--   3. 执行前务必确认已完成备份，并使用具有 CREATE、DROP、ALTER、INDEX 权限的 MySQL 账号。
--   4. 如需开发联调数据，再手动执行 02_seed_test_users_and_trips.sql；生产环境不要执行该文件。

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
DROP TABLE IF EXISTS `app_push_task`;
DROP TABLE IF EXISTS `app_push_device`;
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
DROP TABLE IF EXISTS `trip_search_history`;
DROP TABLE IF EXISTS `trip_favorite`;
DROP TABLE IF EXISTS `trip_leader_rating_summary`;
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
DROP TABLE IF EXISTS `trip_departure_exception`;
DROP TABLE IF EXISTS `trip_arrival_state`;
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
                                            id bigint primary key comment '记录主键',
                                            user_id bigint not null comment '平台用户ID',
                                            phone varchar(20) not null comment '用户手机号',
                                            account_status tinyint not null default 1 comment '账号状态：1正常、2禁用',
                                            mini_invite_onboarding_completed tinyint not null default 0 comment '邀请引导状态：0未完成、1已完成',
                                            last_login_time datetime null comment '最后登录时间',
                                            last_login_ip varchar(64) null comment '最后登录IP地址',
                                            created_at datetime not null comment '记录创建时间',
                                            updated_at datetime not null comment '记录最后更新时间',
                                            deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                            unique key uk_auth_account_phone (phone),
                                            unique key uk_auth_account_user_id (user_id),
                                            key idx_auth_account_status (account_status)
) comment='认证账号表';

create table if not exists auth_sms_log (
                                            id bigint primary key comment '记录主键',
                                            phone varchar(20) not null comment '用户手机号',
                                            scene varchar(32) not null comment '业务场景',
                                            send_status tinyint not null comment '发送结果：1成功、2失败',
                                            provider varchar(64) null comment '数据或服务提供方',
                                            error_message varchar(255) null comment '错误信息',
                                            created_at datetime not null comment '记录创建时间',
                                            key idx_auth_sms_log_phone_scene (phone, scene),
                                            key idx_auth_sms_log_created_at (created_at)
) comment='认证短信发送日志表';

create table if not exists auth_login_log (
                                              id bigint primary key comment '记录主键',
                                              user_id bigint null comment '平台用户ID',
                                              phone varchar(20) null comment '用户手机号',
                                              action_type varchar(32) not null comment 'login/password_login/wx_phone_login/app_login/logout/refresh',
                                              device_id varchar(128) null comment '设备ID',
                                              ip varchar(64) null comment '请求来源IP地址',
                                              success tinyint not null comment '操作结果：1成功、0失败',
                                              message varchar(255) null comment '消息',
                                              created_at datetime not null comment '记录创建时间',
                                              key idx_auth_login_log_user_id (user_id),
                                              key idx_auth_login_log_phone (phone),
                                              key idx_auth_login_log_action_type (action_type),
                                              key idx_auth_login_log_created_at (created_at)
) comment='认证登录日志表';

create table if not exists auth_device_binding (
                                                   id bigint primary key comment '记录主键',
                                                   user_id bigint not null comment '平台用户ID',
                                                   phone varchar(20) not null comment '用户手机号',
                                                   client_type varchar(32) not null comment '客户端类型',
                                                   device_id varchar(128) not null comment '设备ID',
                                                   device_name varchar(128) null comment '设备名称',
                                                   platform varchar(32) null comment '平台',
                                                   bind_status tinyint not null default 1 comment '设备绑定状态：1正常、2已解绑、3风控冻结',
                                                   last_login_time datetime null comment '最后登录时间',
                                                   last_login_ip varchar(64) null comment '最后登录IP地址',
                                                   created_at datetime not null comment '记录创建时间',
                                                   updated_at datetime not null comment '记录最后更新时间',
                                                   deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                   unique key uk_auth_device_user_client_device (user_id, client_type, device_id, deleted),
                                                   key idx_auth_device_user_client (user_id, client_type),
                                                   key idx_auth_device_phone (phone)
) comment='认证设备绑定表';

create table if not exists auth_password_credential (
                                                        id bigint primary key comment '记录主键',
                                                        user_id bigint not null comment '平台用户ID',
                                                        password_hash varchar(255) not null comment '密码安全哈希值',
                                                        password_version varchar(32) not null default 'BCRYPT' comment '密码版本号',
                                                        password_status tinyint not null default 1 comment '密码状态：1正常、2冻结或不可用',
                                                        last_set_time datetime not null comment '最后设置时间',
                                                        created_at datetime not null comment '记录创建时间',
                                                        updated_at datetime not null comment '记录最后更新时间',
                                                        deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                        unique key uk_auth_password_user (user_id, deleted)
) comment='认证密码凭证表';

create table if not exists auth_user_role (
                                              user_id bigint not null comment '平台用户ID',
                                              role_code varchar(32) not null comment '角色编码',
                                              granted_at datetime not null default current_timestamp comment '授予时间',
                                              primary key (user_id, role_code)
) comment='认证用户角色表';

-- ============================================================================
-- user-module
-- source: user-module/src/main/resources/db/user-schema.sql
-- ============================================================================
create table if not exists user_profile (
                                            id bigint not null comment '用户资料记录主键，使用雪花算法生成',
                                            user_id bigint not null comment '平台内部用户ID，用于关联认证、隐私设置及其他业务数据',
                                            tongluxing_id varchar(32) not null comment '面向用户公开展示且创建后不可修改的同路行号',
                                            nickname varchar(32) not null default '' comment '用户昵称',
                                            avatar_image_key varchar(512) not null default '' comment '用户头像在对象存储中的文件Key，不保存临时访问URL',
                                            gender tinyint not null default 0 comment '用户性别编码：0未知、1男、2女',
                                            birthday date null comment '用户生日',
                                            city_code varchar(16) null comment '用户所在城市的标准行政区划编码',
                                            city_name varchar(64) null comment '用户所在城市的展示名称',
                                            bio varchar(200) null comment '用户个人简介',
                                            profile_status varchar(16) not null default 'ACTIVE' comment '用户资料状态，例如ACTIVE表示正常可用',
                                            created_at datetime not null default current_timestamp comment '用户资料创建时间',
                                            updated_at datetime not null default current_timestamp on update current_timestamp comment '用户资料最后更新时间',
                                            deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                            primary key (id),
                                            unique key uk_user_profile_user (user_id, deleted),
                                            unique key uk_user_profile_tongluxing_id (tongluxing_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='用户基础资料表';

create table if not exists user_driving_license_certification (
                                                                  id bigint not null comment '驾驶证认证申请主键，使用雪花算法生成',
                                                                  user_id bigint not null comment '提交驾驶证认证的用户ID',
                                                                  holder_name_cipher varchar(256) not null comment '驾驶证持证人姓名的AES-GCM加密密文',
                                                                  license_no_cipher varchar(512) not null comment '完整驾驶证号码的AES-GCM加密密文',
                                                                  license_no_mask varchar(32) not null comment '用于列表安全展示的脱敏驾驶证号码',
                                                                  vehicle_class varchar(32) not null comment '驾驶证准驾车型',
                                                                  first_issue_date date null comment '驾驶证初次领证日期',
                                                                  valid_from date null comment '驾驶证当前有效期开始日期',
                                                                  valid_to date null comment '驾驶证当前有效期截止日期',
                                                                  issuing_authority varchar(128) null comment '驾驶证发证机关名称',
                                                                  license_front_image_key varchar(512) not null comment '驾驶证主页图片在对象存储中的文件Key',
                                                                  license_back_image_key varchar(512) null comment '驾驶证副页图片在对象存储中的文件Key',
                                                                  recognition_source varchar(32) not null default 'MINIPROGRAM_OCR' comment '证件信息识别来源：MINIPROGRAM_OCR小程序识别、MANUAL_UPLOAD手动上传',
                                                                  certification_status varchar(20) not null default 'PENDING' comment '认证状态：PENDING待审核、APPROVED已通过、REJECTED已驳回',
                                                                  reject_reason varchar(255) null comment '认证审核未通过时的驳回原因',
                                                                  reviewer_id bigint null comment '执行认证审核的后台管理员用户ID',
                                                                  submitted_at datetime not null comment '用户提交本次认证申请的时间',
                                                                  reviewed_at datetime null comment '后台完成本次认证审核的时间',
                                                                  created_at datetime not null default current_timestamp comment '认证申请记录创建时间',
                                                                  updated_at datetime not null default current_timestamp on update current_timestamp comment '认证申请记录最后更新时间',
                                                                  deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                                  primary key (id),
                                                                  key idx_driver_cert_user_submit (user_id, submitted_at),
                                                                  key idx_driver_cert_status_submit (certification_status, submitted_at),
                                                                  key idx_driver_cert_reviewer (reviewer_id, reviewed_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='用户驾驶证认证申请及审核记录表';

create table if not exists user_privacy_setting (
                                                    id bigint not null comment '用户隐私设置记录主键，使用雪花算法生成',
                                                    user_id bigint not null comment '隐私设置所属的平台用户ID',
                                                    profile_visibility varchar(16) not null default 'PUBLIC' comment '用户公开主页可见范围：PUBLIC公开、PRIVATE私密',
                                                    vehicle_visibility varchar(16) not null default 'TEAM_ONLY' comment '用户车辆信息可见范围：PUBLIC公开、TEAM_ONLY仅队友、PRIVATE私密',
                                                    invite_enabled_flag tinyint not null default 1 comment '是否启用邀请功能：0关闭、1启用',
                                                    city_visible_flag tinyint not null default 1 comment '是否在公开主页展示所在城市：0隐藏、1展示',
                                                    bio_visible_flag tinyint not null default 1 comment '是否在公开主页展示个人简介：0隐藏、1展示',
                                                    trip_stats_visible_flag tinyint not null default 1 comment '是否在公开主页展示行程统计：0隐藏、1展示',
                                                    level_visible_flag tinyint not null default 1 comment '是否在公开主页展示用户等级：0隐藏、1展示',
                                                    location_enabled_flag tinyint not null default 1 comment '是否允许使用定位相关功能：0关闭、1启用',
                                                    notification_enabled_flag tinyint not null default 1 comment '是否允许接收业务通知：0关闭、1启用',
                                                    created_at datetime not null default current_timestamp comment '隐私设置记录创建时间',
                                                    updated_at datetime not null default current_timestamp on update current_timestamp comment '隐私设置最后更新时间',
                                                    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                    primary key (id),
                                                    unique key uk_user_privacy_user (user_id, deleted)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='用户隐私及功能授权设置表';

create table if not exists user_statistics (
                                               user_id bigint not null comment '平台用户ID，同时作为本表主键',
                                               total_trip_count int not null default 0 comment '用户累计完成或参与的行程数量',
                                               total_distance_meters bigint not null default 0 comment '用户累计行程距离，单位为米',
                                               total_duration_minutes bigint not null default 0 comment '用户累计行程时长，单位为分钟',
                                               completed_waypoint_count int not null default 0 comment '用户累计完成的行程途经点数量',
                                               updated_at datetime not null default current_timestamp on update current_timestamp comment '用户行程统计最后更新时间',
                                               primary key (user_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='用户行程累计统计表';

create table if not exists user_follow (
                                           id bigint not null comment '用户关注关系主键，使用雪花算法生成',
                                           follower_user_id bigint not null comment '发起关注行为的用户ID',
                                           followed_user_id bigint not null comment '被关注的目标用户ID',
                                           created_at datetime not null default current_timestamp comment '关注关系建立时间',
                                           primary key (id),
                                           unique key uk_user_follow_relation (follower_user_id, followed_user_id),
                                           key idx_user_follow_followed (followed_user_id, created_at),
                                           key idx_user_follow_follower (follower_user_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='用户关注关系表';

-- ============================================================================
-- growth-module
-- source: growth-module/src/main/resources/db/growth-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS growth_account (
                                              id BIGINT NOT NULL comment '记录主键',
                                              user_id BIGINT NOT NULL comment '平台用户ID',
                                              total_points INT NOT NULL DEFAULT 0 comment '累计成长值',
                                              level_code VARCHAR(16) NOT NULL DEFAULT 'LV1' comment '等级编码',
                                              version INT NOT NULL DEFAULT 0 comment '版本',
                                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                              deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                              PRIMARY KEY (id),
                                              UNIQUE KEY uk_growth_account_user (user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='成长值账号表';
CREATE TABLE IF NOT EXISTS growth_log (
                                          id BIGINT NOT NULL comment '记录主键',
                                          user_id BIGINT NOT NULL comment '平台用户ID',
                                          biz_type VARCHAR(32) NOT NULL comment '关联业务类型',
                                          biz_id VARCHAR(64) NOT NULL comment '关联业务单据标识',
                                          point_delta INT NOT NULL comment '本次成长值变动值',
                                          balance_after INT NOT NULL comment 'BALANCE变更后',
                                          remark VARCHAR(255) NULL comment '业务备注',
                                          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                          deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                          PRIMARY KEY (id),
                                          UNIQUE KEY uk_growth_log_biz (biz_type, biz_id, user_id),
                                          KEY idx_growth_log_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='成长值LOG表';
CREATE TABLE IF NOT EXISTS growth_level_rule (
                                                 id BIGINT NOT NULL comment '记录主键',
                                                 level_code VARCHAR(16) NOT NULL comment '等级编码',
                                                 level_name VARCHAR(32) NOT NULL comment '等级名称',
                                                 min_points INT NOT NULL comment '等级所需最低成长值',
                                                 max_points INT NULL comment '等级所需最高成长值',
                                                 benefit_json JSON NOT NULL comment '权益JSON数据',
                                                 enabled_flag TINYINT NOT NULL DEFAULT 1 comment '是否启用：0否、1是',
                                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                 deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                 PRIMARY KEY (id),
                                                 UNIQUE KEY uk_growth_level_code (level_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='成长值等级规则表';
CREATE TABLE IF NOT EXISTS growth_badge (
                                            id BIGINT NOT NULL comment '记录主键',
                                            badge_code VARCHAR(32) NOT NULL comment '徽章编码',
                                            badge_name VARCHAR(64) NOT NULL comment '徽章名称',
                                            badge_image_key VARCHAR(512) NULL comment '徽章图片标识或存储Key',
                                            condition_description VARCHAR(128) NOT NULL comment '条件说明',
                                            event_type VARCHAR(32) NOT NULL comment '事件类型',
                                            threshold INT NOT NULL comment '业务达成阈值',
                                            enabled_flag TINYINT NOT NULL DEFAULT 1 comment '是否启用：0否、1是',
                                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                            deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                            PRIMARY KEY (id),
                                            UNIQUE KEY uk_growth_badge_code (badge_code, deleted),
                                            KEY idx_growth_badge_event_threshold (event_type, enabled_flag, deleted, threshold)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='成长值徽章表';
CREATE TABLE IF NOT EXISTS growth_user_badge (
                                                 id BIGINT NOT NULL comment '记录主键',
                                                 user_id BIGINT NOT NULL comment '平台用户ID',
                                                 badge_id BIGINT NOT NULL comment '徽章ID',
                                                 source_biz_id VARCHAR(64) NULL comment '来源业务ID',
                                                 awarded_at DATETIME NOT NULL comment '获得时间',
                                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                 deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                 PRIMARY KEY (id),
                                                 UNIQUE KEY uk_growth_user_badge (user_id, badge_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='成长值用户徽章表';

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
                                           id BIGINT NOT NULL comment '记录主键',
                                           user_id BIGINT NOT NULL comment '平台用户ID',
                                           invite_code VARCHAR(16) NOT NULL comment '邀请编码',
                                           enabled_flag TINYINT NOT NULL DEFAULT 1 comment '是否启用：0否、1是',
                                           created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                           updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                           deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                           PRIMARY KEY (id),
                                           UNIQUE KEY uk_invite_code_user (user_id, deleted),
                                           UNIQUE KEY uk_invite_code_value (invite_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='邀请编码表';
CREATE TABLE IF NOT EXISTS invite_relation (
                                               id BIGINT NOT NULL comment '记录主键',
                                               inviter_user_id BIGINT NOT NULL comment '邀请人用户ID',
                                               invitee_user_id BIGINT NOT NULL comment '被邀请人用户ID',
                                               invite_code VARCHAR(16) NULL comment '邀请编码',
                                               relation_status VARCHAR(16) NOT NULL DEFAULT 'REGISTERED' comment '关系状态',
                                               bind_source VARCHAR(32) NOT NULL DEFAULT 'MANUAL_CODE' comment '绑定来源',
                                               bind_source_value VARCHAR(64) NULL comment '绑定来源值',
                                               request_id VARCHAR(64) NULL comment '请求唯一标识，用于链路追踪或幂等控制',
                                               invitee_registered_at DATETIME NULL comment '被邀请人REGISTERED时间',
                                               bound_at DATETIME NOT NULL comment 'BOUND时间',
                                               first_team_completed_at DATETIME NULL comment 'FIRST队伍完成时间',
                                               created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                               updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                               deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                               PRIMARY KEY (id),
                                               UNIQUE KEY uk_invite_relation_invitee (invitee_user_id, deleted),
                                               UNIQUE KEY uk_invite_relation_request (request_id, deleted),
                                               KEY idx_invite_relation_inviter (inviter_user_id, relation_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='邀请关系表';
CREATE TABLE IF NOT EXISTS invite_reward_record (
                                                    id BIGINT NOT NULL comment '记录主键',
                                                    relation_id BIGINT NOT NULL comment '关系ID',
                                                    beneficiary_user_id BIGINT NOT NULL comment '受益人用户ID',
                                                    inviter_user_id BIGINT NOT NULL comment '邀请人用户ID',
                                                    invitee_user_id BIGINT NOT NULL comment '被邀请人用户ID',
                                                    rule_code VARCHAR(64) NOT NULL comment '规则编码',
                                                    reward_rule_code VARCHAR(64) NOT NULL comment '奖励规则编码',
                                                    reward_type VARCHAR(32) NOT NULL DEFAULT 'GROWTH_VALUE' comment '奖励类型',
                                                    reward_value INT NOT NULL DEFAULT 0 comment '奖励值',
                                                    reward_biz_no VARCHAR(64) NOT NULL comment '奖励业务编号',
                                                    idempotency_key VARCHAR(128) NOT NULL comment '业务幂等键，用于防止重复处理',
                                                    reward_snapshot_json JSON NOT NULL comment '奖励快照JSON数据',
                                                    reward_status VARCHAR(16) NOT NULL DEFAULT 'PENDING' comment '奖励状态',
                                                    triggered_at DATETIME NOT NULL comment 'TRIGGERED时间',
                                                    failure_reason VARCHAR(255) NULL comment '失败原因',
                                                    granted_at DATETIME NULL comment '授予时间',
                                                    issued_at DATETIME NULL comment '发放时间',
                                                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                    deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                    PRIMARY KEY (id),
                                                    UNIQUE KEY uk_invite_reward_biz (reward_biz_no, deleted),
                                                    UNIQUE KEY uk_invite_reward_idempotency (idempotency_key, deleted),
                                                    UNIQUE KEY uk_invite_reward_relation_rule (relation_id, rule_code, deleted),
                                                    KEY idx_invite_reward_user (beneficiary_user_id, reward_status, created_at),
                                                    KEY idx_invite_reward_invitee (invitee_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='邀请奖励记录表';

CREATE TABLE IF NOT EXISTS invite_reward_rule (
                                                  id BIGINT NOT NULL PRIMARY KEY comment '记录主键',
                                                  rule_code VARCHAR(64) NOT NULL comment '规则编码',
                                                  reward_type VARCHAR(32) NOT NULL DEFAULT 'GROWTH_VALUE' comment '奖励类型',
                                                  reward_value INT NOT NULL comment '奖励值',
                                                  status VARCHAR(16) NOT NULL DEFAULT 'ENABLED' comment '业务状态',
                                                  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                  UNIQUE KEY uk_invite_reward_rule_code (rule_code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='邀请奖励规则表';

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
                                               id BIGINT NOT NULL comment '记录主键',
                                               coupon_name VARCHAR(64) NOT NULL comment '优惠券名称',
                                               coupon_type VARCHAR(24) NOT NULL comment '优惠券类型',
                                               issuer_id BIGINT NULL comment '发行方ID',
                                               threshold_amount DECIMAL(10,2) NOT NULL DEFAULT 0 comment '门槛金额',
                                               discount_amount DECIMAL(10,2) NOT NULL comment '优惠金额',
                                               scope_json JSON NOT NULL comment '适用范围JSON数据',
                                               validity_type VARCHAR(16) NOT NULL comment '有效期类型',
                                               valid_days INT NULL comment '有效，单位为天',
                                               valid_start_at DATETIME NULL comment '有效起点时间',
                                               valid_end_at DATETIME NULL comment '有效终点时间',
                                               total_quantity INT NOT NULL comment '总计数量',
                                               claimed_quantity INT NOT NULL DEFAULT 0 comment '已领取数量',
                                               per_user_limit INT NOT NULL DEFAULT 1 comment '每名用户可领取数量上限',
                                               template_status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' comment '模板状态',
                                               created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                               updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                               deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                               PRIMARY KEY (id),
                                               KEY idx_coupon_template_status (template_status, valid_start_at, valid_end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='优惠券模板表';
CREATE TABLE IF NOT EXISTS coupon_user (
                                           id BIGINT NOT NULL comment '记录主键',
                                           user_id BIGINT NOT NULL comment '平台用户ID',
                                           template_id BIGINT NOT NULL comment '模板ID',
                                           source_type VARCHAR(24) NOT NULL comment '来源类型',
                                           source_biz_id VARCHAR(64) NOT NULL comment '来源业务ID',
                                           coupon_status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE' comment '优惠券状态',
                                           valid_start_at DATETIME NOT NULL comment '有效起点时间',
                                           valid_end_at DATETIME NOT NULL comment '有效终点时间',
                                           locked_order_id BIGINT NULL comment '锁定订单ID',
                                           used_order_id BIGINT NULL comment '使用订单ID',
                                           used_at DATETIME NULL comment '使用时间',
                                           created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                           updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                           deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                           PRIMARY KEY (id),
                                           UNIQUE KEY uk_coupon_user_source (user_id, template_id, source_type, source_biz_id, deleted),
                                           KEY idx_coupon_user_status (user_id, coupon_status, valid_end_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='优惠券用户表';

-- ============================================================================
-- vehicle-module
-- source: vehicle-module/src/main/resources/db/vehicle-schema.sql
-- ============================================================================
create table if not exists vehicle_profile (
                                               id bigint primary key comment '记录主键',
                                               user_id bigint not null comment '平台用户ID',
                                               plate_no_cipher varchar(256) null comment '车牌NO加密密文',
                                               plate_no_mask varchar(32) null comment '车牌NO脱敏展示值',
                                               brand varchar(64) not null default '' comment '品牌',
                                               model varchar(64) not null default '' comment '车型',
                                               vehicle_type varchar(32) not null default '' comment '车辆类型',
                                               color varchar(32) not null default '' comment '颜色',
                                               seat_count tinyint not null default 5 comment '座位数量',
                                               energy_type varchar(32) not null default '' comment '能源类型',
                                               vehicle_photo_image_key varchar(512) not null default '' comment '车辆照片图片标识或存储Key',
                                               certification_status varchar(32) not null default 'UNSUBMITTED' comment '认证状态',
                                               is_default tinyint not null default 0 comment '是否为默认记录：0否、1是',
                                               created_at datetime not null comment '记录创建时间',
                                               updated_at datetime not null comment '记录最后更新时间',
                                               deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                               key idx_vehicle_profile_user (user_id, deleted),
                                               key idx_vehicle_profile_default (user_id, is_default, deleted),
                                               key idx_vehicle_profile_status (certification_status)
) comment='车辆资料表';

create table if not exists vehicle_certification (
                                                     id bigint primary key comment '记录主键',
                                                     vehicle_id bigint not null comment '车辆ID',
                                                     user_id bigint not null comment '平台用户ID',
                                                     owner_name varchar(64) not null default '' comment '队长名称',
                                                     plate_no_cipher varchar(256) null comment '车牌NO加密密文',
                                                     plate_no_mask varchar(32) null comment '车牌NO脱敏展示值',
                                                     vehicle_type varchar(32) not null default '' comment '车辆类型',
                                                     vin_cipher varchar(256) null comment '车架号加密密文',
                                                     vin_mask varchar(32) null comment '车架号脱敏展示值',
                                                     engine_no_cipher varchar(256) null comment '发动机NO加密密文',
                                                     engine_no_mask varchar(32) null comment '发动机NO脱敏展示值',
                                                     register_date date null comment '注册日期',
                                                     issue_date date null comment '签发日期',
                                                     issuing_authority varchar(128) null comment '发证机关',
                                                     license_front_image_key varchar(512) not null comment '驾驶证FRONT图片标识或存储Key',
                                                     license_back_image_key varchar(512) null comment '驾驶证BACK图片标识或存储Key',
                                                     recognition_source varchar(32) not null default 'MINIPROGRAM_OCR' comment '识别来源',
                                                     status varchar(32) not null default 'PENDING' comment '业务状态',
                                                     reject_reason varchar(255) not null default '' comment '驳回原因',
                                                     submitted_at datetime not null comment 'SUBMITTED时间',
                                                     reviewed_at datetime null comment '审核时间',
                                                     reviewer_id bigint null comment '审核人ID',
                                                     key idx_vehicle_cert_vehicle (vehicle_id, submitted_at),
                                                     key idx_vehicle_cert_user (user_id),
                                                     key idx_vehicle_cert_status (status),
                                                     key idx_vehicle_cert_plate_status (plate_no_cipher, status)
) comment='车辆认证表';

create table if not exists vehicle_certification_image (
                                                           id bigint primary key comment '记录主键',
                                                           certification_id bigint not null comment '认证ID',
                                                           vehicle_id bigint not null comment '车辆ID',
                                                           image_type varchar(32) not null comment '图片类型',
                                                           image_key varchar(512) not null comment '图片标识或存储Key',
                                                           sort_no int not null default 0 comment 'SORT编号',
                                                           created_at datetime not null comment '记录创建时间',
                                                           deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                           unique key uk_vehicle_cert_image (certification_id, image_type, sort_no, deleted),
                                                           key idx_vehicle_cert_image_vehicle (vehicle_id),
                                                           key idx_vehicle_cert_image_cert (certification_id)
) comment='车辆认证图片表';

create table if not exists vehicle_audit_log (
                                                 id bigint primary key comment '记录主键',
                                                 vehicle_id bigint null comment '车辆ID',
                                                 user_id bigint not null comment '平台用户ID',
                                                 operation_type varchar(32) not null comment '操作类型',
                                                 before_snapshot text null comment '操作前数据快照',
                                                 after_snapshot text null comment '操作后数据快照',
                                                 remark varchar(255) null comment '业务备注',
                                                 created_at datetime not null comment '记录创建时间',
                                                 key idx_vehicle_audit_vehicle (vehicle_id),
                                                 key idx_vehicle_audit_user (user_id),
                                                 key idx_vehicle_audit_type (operation_type)
) comment='车辆审核LOG表';

-- ============================================================================
-- trip-module
-- source: trip-module/src/main/resources/db/trip-schema.sql
-- ============================================================================
create table if not exists trip (
                                    id bigint primary key comment '记录主键',
                                    trip_number varchar(20) not null comment '对外展示的行程编号',
                                    user_id bigint not null comment '发布者用户ID',
                                    trip_type varchar(24) not null default 'DRIVER_TRIP' comment '行程类型：DRIVER_TRIP车主行程、PASSENGER_DEMAND乘客需求',
                                    publisher_role varchar(16) not null default 'DRIVER' comment '发布者身份：DRIVER、PASSENGER',
                                    captain_user_id bigint null comment '当前队长用户ID；乘客需求未匹配时为空',
                                    vehicle_id bigint null comment '发布者车辆ID；乘客需求为空',
                                    title varchar(128) not null default '' comment '展示标题',
                                    description varchar(1000) not null default '' comment '详细说明',
                                    cover_image_key varchar(512) null comment '封面图在对象存储中的文件Key',
                                    expected_people int null comment '预计人数',
                                    start_name varchar(128) not null comment '起点名称',
                                    start_lat decimal(10,6) null comment '起点纬度',
                                    start_lng decimal(10,6) null comment '起点经度',
                                    start_location_name varchar(128) not null default '' comment '起点位置名称',
                                    start_location_address varchar(255) not null default '' comment '起点位置地址',
                                    start_latitude decimal(10,6) null comment '起点纬度',
                                    start_longitude decimal(10,6) null comment '起点经度',
                                    end_name varchar(128) not null comment '终点名称',
                                    end_lat decimal(10,6) null comment '终点纬度',
                                    end_lng decimal(10,6) null comment '终点经度',
                                    end_location_name varchar(128) not null default '' comment '终点位置名称',
                                    end_location_address varchar(255) not null default '' comment '终点位置地址',
                                    end_latitude decimal(10,6) null comment '终点纬度',
                                    end_longitude decimal(10,6) null comment '终点经度',
                                    route_summary varchar(255) null comment '路线汇总',
                                    route_polyline_key varchar(512) null comment '路线折线数据的对象存储Key',
                                    route_distance int null comment '路线距离',
                                    route_duration int null comment '路线时长',
                                    route_polyline mediumtext null comment '路线折线编码数据',
                                    waypoints_json text null comment '途经点列表JSON数据',
                                    departure_time datetime not null comment '出发时间',
                                    estimated_days int null comment '估算，单位为天',
                                    total_distance_meters int null comment '总计距离，单位为米',
                                    max_vehicle_count int not null comment '最高车辆数量',
                                    joined_vehicle_count int not null default 1 comment '加入车辆数量',
                                    vehicle_requirements varchar(128) not null default '不限' comment '车辆要求',
                                    budget_description varchar(128) null comment '预算说明',
                                    travel_depth varchar(16) not null comment '出行深度',
                                    public_flag tinyint(1) not null default 1 comment '是否公开：0否、1是',
                                    status varchar(20) not null comment '业务状态',
                                    auto_start_enabled tinyint(1) not null default 1 comment '是否到点自动出发',
                                    arrival_status varchar(24) not null default 'NOT_ARRIVED' comment '到达状态',
                                    arrival_entered_at datetime null comment '首次进入终点范围时间',
                                    arrival_decision_deadline datetime null comment '到达后最迟处理时间',
                                    continue_count int not null default 0 comment '继续行程次数',
                                    remark varchar(255) null comment '业务备注',
                                    actual_start_time datetime null comment '实际起点时间',
                                    actual_end_time datetime null comment '实际终点时间',
                                    created_at datetime not null comment '记录创建时间',
                                    updated_at datetime not null comment '记录最后更新时间',
                                    deleted tinyint(1) not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                    unique key uk_trip_number (trip_number),
                                    key idx_trip_user_status_time (user_id, status, departure_time),
                                    key idx_trip_captain_status (captain_user_id, status, departure_time),
                                    key idx_trip_public_status_time (public_flag, status, departure_time),
                                    key idx_trip_auto_start (auto_start_enabled, status, departure_time),
                                    key idx_trip_arrival_deadline (arrival_status, arrival_decision_deadline),
                                    key idx_trip_vehicle (vehicle_id)
) comment='行程表';


create table if not exists trip_route (
                                          id bigint primary key comment '记录主键',
                                          trip_id bigint null comment '行程ID',
                                          draft_id bigint null comment '草稿ID',
                                          route_plan_id bigint null comment '路线规划ID',
                                          origin json not null comment 'ORIGIN',
                                          destination json not null comment 'DESTINATION',
                                          waypoints json null comment '途经点',
                                          polyline mediumtext null comment 'POLYLINE',
                                          plan_distance int null comment '规划距离',
                                          plan_duration int null comment '规划时长',
                                          provider_type varchar(32) not null comment '外部服务提供方类型',
                                          route_status varchar(16) not null default 'VALID' comment '路线状态',
                                          created_at datetime not null comment '记录创建时间',
                                          updated_at datetime not null comment '记录最后更新时间',
                                          deleted tinyint(1) not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                          unique key uk_trip_route_trip (trip_id, deleted),
                                          unique key uk_trip_route_draft (draft_id, deleted)
) comment='行程路线表';

create table if not exists trip_waypoint (
                                             id bigint primary key comment '记录主键',
                                             trip_id bigint null comment '行程ID',
                                             draft_id bigint null comment '草稿ID',
                                             seq_no int not null comment '序号编号',
                                             place_name varchar(128) not null comment 'PLACE名称',
                                             place_address varchar(255) not null default '' comment 'PLACE地址',
                                             waypoint_type varchar(16) not null default 'REST' comment '途经点类型',
                                             lat decimal(10,6) null comment '纬度坐标',
                                             lng decimal(10,6) null comment '经度坐标',
                                             stay_minutes int null comment 'STAY，单位为分钟',
                                             remark varchar(255) not null default '' comment '业务备注',
                                             created_at datetime not null comment '记录创建时间',
                                             updated_at datetime not null comment '记录最后更新时间',
                                             deleted tinyint(1) not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                             key idx_waypoint_trip_seq (trip_id, seq_no),
                                             key idx_waypoint_draft_seq (draft_id, seq_no)
) comment='行程途经点表';

create table if not exists trip_member_snapshot (
                                                    id bigint primary key comment '记录主键',
                                                    trip_id bigint not null comment '行程ID',
                                                    user_id bigint not null comment '平台用户ID',
                                                    vehicle_id bigint null comment '车辆ID',
                                                    member_role varchar(16) not null comment '成员角色',
                                                    join_status varchar(20) not null comment '加入状态',
                                                    nickname_snapshot varchar(64) null comment '昵称快照',
                                                    vehicle_snapshot varchar(128) null comment '车辆快照',
                                                    joined_at datetime null comment '加入时间',
                                                    created_at datetime not null comment '记录创建时间',
                                                    updated_at datetime not null comment '记录最后更新时间',
                                                    key idx_member_trip_status (trip_id, join_status)
) comment='行程成员快照表';

create table if not exists trip_audit_log (
                                              id bigint primary key comment '记录主键',
                                              trip_id bigint not null comment '行程ID',
                                              user_id bigint not null comment '平台用户ID',
                                              operation_type varchar(32) not null comment '操作类型',
                                              before_json json null comment '操作前数据快照JSON',
                                              after_json json null comment '操作后数据快照JSON',
                                              remark varchar(255) null comment '业务备注',
                                              created_at datetime not null comment '记录创建时间',
                                              key idx_audit_trip_time (trip_id, created_at)
) comment='行程审核LOG表';

create table if not exists trip_draft (
                                          id bigint primary key comment '记录主键',
                                          user_id bigint not null comment '平台用户ID',
                                          title varchar(128) not null default '' comment '展示标题',
                                          description varchar(1000) not null default '' comment '详细说明',
                                          cover_image_key varchar(512) null comment '封面图在对象存储中的文件Key',
                                          start_location_json json null comment '起点位置JSON数据',
                                          end_location_json json null comment '终点位置JSON数据',
                                          waypoint_json json not null comment '途经点JSON数据',
                                          departure_time datetime null comment '出发时间',
                                          duration_days int null comment '时长，单位为天',
                                          people_count int null comment '人数数量',
                                          vehicle_requirements varchar(128) not null default '不限' comment '车辆要求',
                                          budget_description varchar(128) null comment '预算说明',
                                          notes varchar(500) null comment 'NOTES',
                                          remark varchar(255) not null default '' comment '业务备注',
                                          draft_status varchar(16) not null default 'DRAFT' comment '草稿状态',
                                          published_trip_id bigint null comment '已发布行程ID',
                                          publish_idempotency_key varchar(64) null comment '发布幂等标识或存储Key',
                                          created_at datetime not null default current_timestamp comment '记录创建时间',
                                          updated_at datetime not null default current_timestamp on update current_timestamp comment '记录最后更新时间',
                                          deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                          key idx_trip_draft_user_status (user_id, draft_status, updated_at),
                                          unique key uk_trip_draft_publish_key (publish_idempotency_key, deleted)
) comment='行程草稿表';

create table if not exists trip_departure_exception (
                                                        id bigint primary key comment '记录主键',
                                                        trip_id bigint not null comment '行程ID',
                                                        member_user_id bigint not null comment '异常成员用户ID',
                                                        distance_m int null comment '成员与队长距离，米',
                                                        exception_type varchar(32) not null comment '异常类型：OUT_OF_RANGE、LOCATION_MISSING',
                                                        exception_status varchar(20) not null default 'PENDING' comment '处理状态：PENDING、WAITING、IGNORED、RESOLVED',
                                                        handled_action varchar(20) null comment '处理动作：WAIT、CONTINUE',
                                                        detected_at datetime not null comment '检测时间',
                                                        handled_at datetime null comment '处理时间',
                                                        created_at datetime not null comment '创建时间',
                                                        updated_at datetime not null comment '更新时间',
                                                        deleted tinyint(1) not null default 0 comment '逻辑删除',
                                                        unique key uk_trip_departure_exception (trip_id, member_user_id, deleted),
                                                        key idx_trip_departure_pending (trip_id, exception_status, detected_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='自动出发成员范围异常表';

create table if not exists trip_arrival_state (
                                                  id bigint primary key comment '记录主键',
                                                  trip_id bigint not null comment '行程ID',
                                                  captain_user_id bigint not null comment '队长用户ID',
                                                  state varchar(24) not null default 'NOT_ARRIVED' comment 'NOT_ARRIVED、DWELLING、AWAITING_DECISION、CONTINUING、ENDED',
                                                  first_entered_at datetime null comment '首次进入终点范围时间',
                                                  prompted_at datetime null comment '满足停留时间后的提示时间',
                                                  decision_deadline datetime null comment '超时自动结束时间',
                                                  last_distance_m int null comment '最近一次到终点距离',
                                                  decision_action varchar(20) null comment 'END、CONTINUE、AUTO_END',
                                                  decided_at datetime null comment '决策时间',
                                                  created_at datetime not null comment '创建时间',
                                                  updated_at datetime not null comment '更新时间',
                                                  deleted tinyint(1) not null default 0 comment '逻辑删除',
                                                  unique key uk_trip_arrival_state (trip_id, deleted),
                                                  key idx_trip_arrival_deadline (state, decision_deadline)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='行程到达停留与开放式结束状态表';

-- ============================================================================
-- map-module
-- source: map-module/src/main/resources/db/map-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS map_route_plan (
                                              id BIGINT NOT NULL comment '记录主键',
                                              user_id BIGINT NOT NULL comment '平台用户ID',
                                              route_hash VARCHAR(64) NOT NULL comment '路线哈希值',
                                              route_points_json JSON NOT NULL comment '路线坐标点JSON数据',
                                              route_result_json JSON NULL comment '路线规划结果JSON数据',
                                              provider_type VARCHAR(32) NOT NULL comment '外部服务提供方类型',
                                              plan_status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' comment '规划状态',
                                              error_message VARCHAR(255) NULL comment '错误信息',
                                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                              deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                              PRIMARY KEY (id),
                                              UNIQUE KEY uk_map_route_hash (route_hash, provider_type, deleted),
                                              KEY idx_map_route_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='地图路线规划表';

CREATE TABLE IF NOT EXISTS map_location_search_log (
                                                       id BIGINT NOT NULL comment '记录主键',
                                                       user_id BIGINT NOT NULL comment '平台用户ID',
                                                       keyword VARCHAR(128) NULL comment '搜索关键词',
                                                       selected_name VARCHAR(128) NULL comment '选择名称',
                                                       selected_address VARCHAR(255) NULL comment '选择地址',
                                                       selected_latitude DECIMAL(10,6) NULL comment '选择纬度',
                                                       selected_longitude DECIMAL(10,6) NULL comment '选择经度',
                                                       scene VARCHAR(32) NOT NULL comment '业务场景',
                                                       provider_type VARCHAR(32) NOT NULL comment '外部服务提供方类型',
                                                       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                       updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                       deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                       PRIMARY KEY (id),
                                                       KEY idx_map_search_user_scene_time (user_id, scene, deleted, created_at),
                                                       KEY idx_map_search_keyword (keyword)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='地图位置搜索日志表';



CREATE TABLE IF NOT EXISTS map_geocode_cache (
                                                 id BIGINT NOT NULL comment '记录主键',
                                                 location_hash VARCHAR(64) NOT NULL comment '位置哈希值',
                                                 address VARCHAR(255) NULL comment '地址',
                                                 latitude DECIMAL(10,6) NULL comment '纬度坐标',
                                                 longitude DECIMAL(10,6) NULL comment '经度坐标',
                                                 geocode_result_json JSON NULL comment '地理编码结果JSON数据',
                                                 provider_type VARCHAR(32) NOT NULL comment '外部服务提供方类型',
                                                 expire_at DATETIME NULL comment '过期时间',
                                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                 deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                 PRIMARY KEY (id),
                                                 UNIQUE KEY uk_map_geocode_hash (location_hash, provider_type, deleted),
                                                 KEY idx_map_geocode_expire (expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='地图地理编码缓存表';

-- ============================================================================
-- team-module
-- source: team-module/src/main/resources/db/team-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS team (
                                    id BIGINT NOT NULL comment '记录主键',
                                    trip_id BIGINT NOT NULL comment '行程ID',
                                    owner_user_id BIGINT NOT NULL comment '队长用户ID',
                                    owner_vehicle_id BIGINT NOT NULL comment '队长车辆ID',
                                    team_name VARCHAR(64) NOT NULL comment '队伍名称',
                                    team_desc VARCHAR(255) NULL comment '队伍说明',
                                    start_name VARCHAR(128) NOT NULL comment '起点名称',
                                    end_name VARCHAR(128) NOT NULL comment '终点名称',
                                    departure_time DATETIME NOT NULL comment '出发时间',
                                    max_member_count INT NOT NULL comment '最高成员数量',
                                    current_member_count INT NOT NULL DEFAULT 1 comment '当前成员数量',
                                    join_mode VARCHAR(20) NOT NULL DEFAULT 'APPROVAL' comment '加入模式',
                                    recruitment_status VARCHAR(16) NOT NULL DEFAULT 'OPEN' comment '招募状态：OPEN、PAUSED、CLOSED',
                                    allow_midway_join TINYINT(1) NOT NULL DEFAULT 0 comment '行进中是否允许申请加入',
                                    deviation_warning_distance_m INT NOT NULL DEFAULT 50000 comment '一级脱队距离阈值，米',
                                    deviation_warning_minutes INT NOT NULL DEFAULT 30 comment '一级脱队持续时间，分钟',
                                    severe_deviation_distance_m INT NOT NULL DEFAULT 100000 comment '严重脱队距离阈值，米',
                                    severe_deviation_minutes INT NOT NULL DEFAULT 60 comment '严重脱队持续时间，分钟',
                                    missing_location_minutes INT NOT NULL DEFAULT 720 comment '失联阈值，分钟',
                                    join_radius_m INT NOT NULL DEFAULT 100000 comment '出发/途中加入范围，米',
                                    privacy_level VARCHAR(24) NOT NULL DEFAULT 'STANDARD' comment '成员资料公开级别',
                                    team_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' comment '队伍状态',
                                    public_flag TINYINT(1) NOT NULL DEFAULT 1 comment '是否公开：0否、1是',
                                    chat_conversation_id BIGINT NULL comment '聊天会话ID',
                                    notice VARCHAR(255) NULL comment '公告',
                                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                    deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                    PRIMARY KEY (id),
                                    KEY idx_team_trip (trip_id, team_status),
                                    KEY idx_team_owner (owner_user_id, team_status),
                                    KEY idx_team_public_time (public_flag, team_status, departure_time),
                                    KEY idx_team_recruitment (team_status, recruitment_status, allow_midway_join)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='队伍表';

CREATE TABLE IF NOT EXISTS team_member (
                                           id BIGINT NOT NULL comment '记录主键',
                                           team_id BIGINT NOT NULL comment '队伍ID',
                                           user_id BIGINT NOT NULL comment '平台用户ID',
                                           vehicle_id BIGINT NULL comment '成员本人驾驶的车辆ID',
                                           linked_owner_user_id BIGINT NULL comment '同车关联车主用户ID',
                                           linked_vehicle_id BIGINT NULL comment '同车关联车辆ID',
                                           plate_reference VARCHAR(24) NULL comment '手动车牌关联脱敏值',
                                           owner_confirm_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED' comment '车主确认状态',
                                           removed_by_user_id BIGINT NULL comment '移除操作人',
                                           removed_reason VARCHAR(255) NULL comment '移除原因',
                                           member_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' comment '成员角色',
                                           member_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' comment '成员状态',
                                           joined_at DATETIME NOT NULL comment '加入时间',
                                           exited_at DATETIME NULL comment '退出时间',
                                           nickname_snapshot VARCHAR(64) NULL comment '昵称快照',
                                           vehicle_snapshot VARCHAR(128) NULL comment '车辆快照',
                                           created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                           updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                           deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                           PRIMARY KEY (id),
                                           UNIQUE KEY uk_team_member_user (team_id, user_id, deleted),
                                           KEY idx_team_member_team (team_id, member_status),
                                           KEY idx_team_member_user_status (user_id, member_status),
                                           KEY idx_team_member_linked_vehicle (team_id, linked_vehicle_id, member_status),
                                           KEY idx_team_member_external_active (user_id, member_role, member_status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='队伍成员表';

CREATE TABLE IF NOT EXISTS team_join_application (
                                                     id BIGINT NOT NULL comment '记录主键',
                                                     team_id BIGINT NOT NULL comment '队伍ID',
                                                     trip_id BIGINT NULL comment '行程ID',
                                                     applicant_user_id BIGINT NOT NULL comment '申请人用户ID',
                                                     applicant_vehicle_id BIGINT NULL comment '申请人本人车辆ID',
                                                     application_type VARCHAR(16) NOT NULL DEFAULT 'JOIN' comment '申请类型：JOIN、RETURN',
                                                     join_role VARCHAR(16) NOT NULL DEFAULT 'PASSENGER' comment '申请身份：DRIVER、PASSENGER',
                                                     linked_owner_user_id BIGINT NULL comment '希望关联的车主用户ID',
                                                     linked_vehicle_id BIGINT NULL comment '希望关联的队内车辆ID',
                                                     plate_reference VARCHAR(24) NULL comment '手动车牌关联脱敏值',
                                                     current_latitude DECIMAL(10,6) NULL comment '归队申请当前位置纬度',
                                                     current_longitude DECIMAL(10,6) NULL comment '归队申请当前位置经度',
                                                     owner_confirm_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED' comment '车主确认状态',
                                                     reviewer_user_id BIGINT NULL comment '审核人用户ID',
                                                     application_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' comment '申请状态',
                                                     apply_message VARCHAR(255) NULL comment '申请说明',
                                                     join_question_json JSON NULL comment '加入问题JSON数据',
                                                     review_message VARCHAR(255) NULL comment '审核说明',
                                                     reviewed_at DATETIME NULL comment '审核时间',
                                                     created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                     updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                     deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                     PRIMARY KEY (id),
                                                     KEY idx_team_apply_applicant (applicant_user_id, application_status, created_at),
                                                     KEY idx_team_apply_team (team_id, application_status, created_at),
                                                     KEY idx_team_apply_type (team_id, application_type, application_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='队伍加入申请表';

CREATE TABLE IF NOT EXISTS team_audit_log (
                                              id BIGINT NOT NULL comment '记录主键',
                                              team_id BIGINT NOT NULL comment '队伍ID',
                                              operator_user_id BIGINT NOT NULL comment '管理员用户ID',
                                              operation_type VARCHAR(32) NOT NULL comment '操作类型',
                                              before_json JSON NULL comment '操作前数据快照JSON',
                                              after_json JSON NULL comment '操作后数据快照JSON',
                                              remark VARCHAR(255) NULL comment '业务备注',
                                              created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                              updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                              deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                              PRIMARY KEY (id),
                                              KEY idx_team_audit_team_time (team_id, created_at),
                                              KEY idx_team_audit_operator_time (operator_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='队伍审核日志表';

-- ============================================================================
-- chat-module
-- source: chat-module/src/main/resources/db/chat-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS chat_conversation (
                                                 id BIGINT NOT NULL comment '记录主键',
                                                 biz_type VARCHAR(32) NOT NULL comment '关联业务类型',
                                                 biz_id BIGINT NOT NULL comment '关联业务单据标识',
                                                 conversation_name VARCHAR(64) NOT NULL comment '会话名称',
                                                 conversation_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' comment '会话状态',
                                                 provider_type VARCHAR(32) NOT NULL DEFAULT 'TENCENT_IM' comment '固定为腾讯云 IM',
                                                 provider_conversation_key VARCHAR(128) NULL comment '服务商会话标识或存储Key',
                                                 last_message_id BIGINT NULL comment '最后消息ID',
                                                 last_message_preview VARCHAR(120) NULL comment '最后消息预览',
                                                 last_message_at DATETIME NULL comment '最后消息时间',
                                                 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                 deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                 PRIMARY KEY (id),
                                                 UNIQUE KEY uk_chat_biz (biz_type, biz_id, deleted),
                                                 KEY idx_chat_last_time (conversation_status, last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='聊天会话表';

CREATE TABLE IF NOT EXISTS chat_conversation_member (
                                                        id BIGINT NOT NULL comment '记录主键',
                                                        conversation_id BIGINT NOT NULL comment '会话ID',
                                                        user_id BIGINT NOT NULL comment '平台用户ID',
                                                        member_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' comment '成员角色',
                                                        member_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' comment '成员状态',
                                                        unread_count INT NOT NULL DEFAULT 0 comment '未读数量',
                                                        muted_flag TINYINT(1) NOT NULL DEFAULT 0 comment '是否免打扰：0否、1是',
                                                        pinned_flag TINYINT(1) NOT NULL DEFAULT 0 comment '是否置顶：0否、1是',
                                                        last_read_message_id BIGINT NULL comment '最后已读消息ID',
                                                        cleared_before_message_id BIGINT NULL comment '清除变更前消息ID',
                                                        joined_at DATETIME NOT NULL comment '加入时间',
                                                        exited_at DATETIME NULL comment '退出时间',
                                                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                        deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                        PRIMARY KEY (id),
                                                        UNIQUE KEY uk_chat_member (conversation_id, user_id, deleted),
                                                        KEY idx_chat_member_user (user_id, member_status),
                                                        KEY idx_chat_member_conversation (conversation_id, member_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='聊天会话成员表';

CREATE TABLE IF NOT EXISTS chat_join_application (
                                                     id BIGINT NOT NULL comment '记录主键',
                                                     conversation_id BIGINT NOT NULL comment '会话ID',
                                                     applicant_user_id BIGINT NOT NULL comment '申请人用户ID',
                                                     application_message VARCHAR(120) NULL comment '申请说明',
                                                     application_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' comment '申请状态',
                                                     reviewer_user_id BIGINT NULL comment '审核人用户ID',
                                                     reviewed_at DATETIME NULL comment '审核时间',
                                                     created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                     updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                     deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                     PRIMARY KEY (id),
                                                     KEY idx_chat_join_owner_queue (conversation_id, application_status, created_at),
                                                     KEY idx_chat_join_applicant (applicant_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='聊天加入申请表';

CREATE TABLE IF NOT EXISTS chat_message (
                                            id BIGINT NOT NULL comment '记录主键',
                                            conversation_id BIGINT NOT NULL comment '会话ID',
                                            sender_user_id BIGINT NULL comment '发送人用户ID',
                                            message_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' comment '消息类型',
                                            message_payload_json JSON NOT NULL comment '消息载荷JSON数据',
                                            message_status VARCHAR(20) NOT NULL DEFAULT 'NORMAL' comment '消息状态',
                                            provider_message_key VARCHAR(128) NULL comment '服务商消息标识或存储Key',
                                            sent_at DATETIME NOT NULL comment 'SENT时间',
                                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                            deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                            PRIMARY KEY (id),
                                            KEY idx_chat_msg_conversation_time (conversation_id, sent_at),
                                            KEY idx_chat_msg_sender_time (sender_user_id, sent_at),
                                            UNIQUE KEY uk_chat_msg_provider (provider_message_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='聊天消息表';

CREATE TABLE IF NOT EXISTS message_risk (
                                            id BIGINT NOT NULL comment '记录主键',
                                            message_id BIGINT NOT NULL comment '消息ID',
                                            risk_level VARCHAR(16) NOT NULL comment '风险等级',
                                            risk_type VARCHAR(20) NOT NULL comment '风险类型',
                                            confidence INT NOT NULL DEFAULT 0 comment '置信度',
                                            status VARCHAR(20) NOT NULL DEFAULT 'PENDING' comment '业务状态',
                                            matched_rule VARCHAR(80) NULL comment '命中规则',
                                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                            PRIMARY KEY (id),
                                            UNIQUE KEY uk_message_risk_message (message_id),
                                            KEY idx_message_risk_review (status, risk_level, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='消息风险表';

CREATE TABLE IF NOT EXISTS chat_group_item (
                                               id BIGINT NOT NULL comment '记录主键',
                                               conversation_id BIGINT NOT NULL comment '会话ID',
                                               item_type VARCHAR(24) NOT NULL COMMENT 'ANNOUNCEMENT/RESOURCE/POLL/REMINDER',
                                               title VARCHAR(120) NOT NULL comment '展示标题',
                                               content VARCHAR(2000) NULL comment '正文内容',
                                               payload_json JSON NULL comment '业务载荷JSON数据',
                                               item_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' comment '事项状态',
                                               creator_user_id BIGINT NOT NULL comment '创建人用户ID',
                                               created_at DATETIME NOT NULL comment '记录创建时间',
                                               updated_at DATETIME NOT NULL comment '记录最后更新时间',
                                               deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                               PRIMARY KEY (id),
                                               KEY idx_chat_group_item (conversation_id,item_type,item_status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群公告、资料、投票与提醒';

CREATE TABLE IF NOT EXISTS chat_group_vote (
                                               id BIGINT NOT NULL comment '记录主键',
                                               item_id BIGINT NOT NULL comment '事项ID',
                                               option_key VARCHAR(64) NOT NULL comment '选项标识或存储Key',
                                               user_id BIGINT NOT NULL comment '平台用户ID',
                                               created_at DATETIME NOT NULL comment '记录创建时间',
                                               PRIMARY KEY (id),
                                               UNIQUE KEY uk_chat_poll_user (item_id,user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群投票记录';

CREATE TABLE IF NOT EXISTS chat_reminder_dispatch (
                                                      item_id BIGINT NOT NULL comment '事项ID',
                                                      dispatched_at DATETIME NOT NULL comment '分发时间',
                                                      PRIMARY KEY (item_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程提醒去重发送记录';

CREATE TABLE IF NOT EXISTS trip_confirmation (
                                                 id BIGINT NOT NULL comment '记录主键',
                                                 trip_id BIGINT NOT NULL comment '行程ID',
                                                 conversation_id BIGINT NOT NULL comment '会话ID',
                                                 creator_user_id BIGINT NOT NULL comment '创建人用户ID',
                                                 confirmation_status VARCHAR(20) NOT NULL DEFAULT 'OPEN' comment '确认状态',
                                                 created_at DATETIME NOT NULL comment '记录创建时间',
                                                 closed_at DATETIME NULL comment '关闭时间',
                                                 PRIMARY KEY (id),
                                                 KEY idx_trip_confirmation (trip_id,confirmation_status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程开始前成员确认批次';

CREATE TABLE IF NOT EXISTS trip_confirm_record (
                                                   id BIGINT NOT NULL comment '记录主键',
                                                   confirmation_id BIGINT NOT NULL comment '确认ID',
                                                   trip_id BIGINT NOT NULL comment '行程ID',
                                                   user_id BIGINT NOT NULL comment '平台用户ID',
                                                   status VARCHAR(20) NOT NULL DEFAULT 'WAITING' comment '业务状态',
                                                   reject_reason VARCHAR(300) NULL comment '驳回原因',
                                                   confirm_time DATETIME NULL comment '确认时间',
                                                   created_at DATETIME NOT NULL comment '记录创建时间',
                                                   updated_at DATETIME NOT NULL comment '记录最后更新时间',
                                                   PRIMARY KEY (id),
                                                   UNIQUE KEY uk_trip_confirm_member (confirmation_id,user_id),
                                                   KEY idx_trip_confirm_trip (trip_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程成员参加确认记录';

CREATE TABLE IF NOT EXISTS chat_member_location (
                                                    id BIGINT NOT NULL comment '记录主键',
                                                    conversation_id BIGINT NOT NULL comment '会话ID',
                                                    user_id BIGINT NOT NULL comment '平台用户ID',
                                                    latitude DECIMAL(10,7) NOT NULL comment '纬度坐标',
                                                    longitude DECIMAL(10,7) NOT NULL comment '经度坐标',
                                                    speed DECIMAL(8,2) NULL comment '速度',
                                                    sharing_flag TINYINT(1) NOT NULL DEFAULT 1 comment '是否分账：0否、1是',
                                                    recorded_at DATETIME NOT NULL comment 'RECORDED时间',
                                                    updated_at DATETIME NOT NULL comment '记录最后更新时间',
                                                    PRIMARY KEY (id),
                                                    UNIQUE KEY uk_chat_member_location (conversation_id,user_id),
                                                    KEY idx_chat_location_time (conversation_id,recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行程群成员最新共享位置';

CREATE TABLE IF NOT EXISTS chat_report (
                                           id BIGINT NOT NULL comment '记录主键',
                                           conversation_id BIGINT NOT NULL comment '会话ID',
                                           reporter_user_id BIGINT NOT NULL comment '举报人用户ID',
                                           target_type VARCHAR(20) NOT NULL COMMENT 'CONVERSATION/MEMBER/MESSAGE',
                                           target_id VARCHAR(64) NOT NULL comment '目标ID',
                                           report_type VARCHAR(32) NOT NULL comment '举报类型',
                                           reason VARCHAR(500) NOT NULL comment '原因说明',
                                           evidence_json JSON NULL comment '举报或审核证据JSON数据',
                                           report_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' comment '举报状态',
                                           reviewer_id BIGINT NULL comment '审核人ID',
                                           review_note VARCHAR(500) NULL comment 'REVIEW备注',
                                           reviewed_at DATETIME NULL comment '审核时间',
                                           created_at DATETIME NOT NULL comment '记录创建时间',
                                           updated_at DATETIME NOT NULL comment '记录最后更新时间',
                                           PRIMARY KEY (id),
                                           KEY idx_chat_report_review (report_status,created_at),
                                           KEY idx_chat_report_conversation (conversation_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='群聊、成员与消息举报';

-- ============================================================================
-- match-module
-- source: match-module/src/main/resources/db/match-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS match_route_snapshot (
                                                    id BIGINT NOT NULL comment '记录主键',
                                                    trip_id BIGINT NOT NULL comment '行程ID',
                                                    user_id BIGINT NOT NULL comment '平台用户ID',
                                                    vehicle_id BIGINT NOT NULL comment '车辆ID',
                                                    start_name VARCHAR(128) NOT NULL comment '起点名称',
                                                    start_address VARCHAR(255) NULL comment '起点地址',
                                                    start_latitude DECIMAL(10,6) NOT NULL comment '起点纬度',
                                                    start_longitude DECIMAL(10,6) NOT NULL comment '起点经度',
                                                    end_name VARCHAR(128) NOT NULL comment '终点名称',
                                                    end_address VARCHAR(255) NULL comment '终点地址',
                                                    end_latitude DECIMAL(10,6) NOT NULL comment '终点纬度',
                                                    end_longitude DECIMAL(10,6) NOT NULL comment '终点经度',
                                                    route_points_json JSON NULL comment '路线坐标点JSON数据',
                                                    route_distance INT NULL comment '路线距离',
                                                    route_duration INT NULL comment '路线时长',
                                                    departure_time DATETIME NOT NULL comment '出发时间',
                                                    travel_depth VARCHAR(16) NOT NULL comment '出行深度',
                                                    max_vehicle_count INT NOT NULL comment '最高车辆数量',
                                                    public_flag TINYINT(1) NOT NULL DEFAULT 1 comment '是否公开：0否、1是',
                                                    snapshot_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' comment '快照状态',
                                                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                    deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                    PRIMARY KEY (id),
                                                    UNIQUE KEY uk_match_snapshot_trip (trip_id, deleted),
                                                    KEY idx_match_snapshot_public_time (public_flag, snapshot_status, departure_time),
                                                    KEY idx_match_snapshot_start_end (start_latitude, start_longitude, end_latitude, end_longitude),
                                                    KEY idx_match_snapshot_user (user_id, snapshot_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='匹配路线快照表';

CREATE TABLE IF NOT EXISTS match_result (
                                            id BIGINT NOT NULL comment '记录主键',
                                            source_trip_id BIGINT NOT NULL comment '来源行程ID',
                                            target_trip_id BIGINT NOT NULL comment '目标行程ID',
                                            source_user_id BIGINT NOT NULL comment '来源用户ID',
                                            target_user_id BIGINT NOT NULL comment '目标用户ID',
                                            match_score INT NOT NULL comment '匹配评分',
                                            overlap_rate INT NOT NULL comment '重合比例',
                                            distance_gap_meters INT NULL comment '距离差值，单位为米',
                                            departure_gap_minutes INT NULL comment '出发差值，单位为分钟',
                                            score_detail_json JSON NULL comment '评分DETAILJSON数据',
                                            result_status VARCHAR(20) NOT NULL DEFAULT 'VALID' comment '结果状态',
                                            calculated_at DATETIME NOT NULL comment '计算完成时间',
                                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                            deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                            PRIMARY KEY (id),
                                            UNIQUE KEY uk_match_pair (source_trip_id, target_trip_id, deleted),
                                            KEY idx_match_source_score (source_trip_id, result_status, match_score),
                                            KEY idx_match_target (target_trip_id, result_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='匹配结果表';

CREATE TABLE IF NOT EXISTS match_recommend_log (
                                                   id BIGINT NOT NULL comment '记录主键',
                                                   user_id BIGINT NOT NULL comment '平台用户ID',
                                                   trip_id BIGINT NOT NULL comment '行程ID',
                                                   target_trip_id BIGINT NULL comment '目标行程ID',
                                                   target_team_id BIGINT NULL comment '目标队伍ID',
                                                   scene VARCHAR(32) NOT NULL comment '业务场景',
                                                   action_type VARCHAR(32) NOT NULL comment '操作类型',
                                                   request_id VARCHAR(64) NULL comment '请求唯一标识，用于链路追踪或幂等控制',
                                                   extra_json JSON NULL comment '扩展业务信息JSON数据',
                                                   created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                   updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                   deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                   PRIMARY KEY (id),
                                                   KEY idx_match_log_user_time (user_id, created_at),
                                                   KEY idx_match_log_trip_scene (trip_id, scene, action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='匹配推荐LOG表';

CREATE TABLE IF NOT EXISTS trip_favorite (
                                             id BIGINT NOT NULL comment '记录主键',
                                             user_id BIGINT NOT NULL comment '平台用户ID',
                                             trip_id BIGINT NOT NULL comment '行程ID',
                                             created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                             PRIMARY KEY (id),
                                             UNIQUE KEY uk_trip_favorite_user_trip (user_id, trip_id),
                                             KEY idx_trip_favorite_user_time (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程收藏表';

-- App 行程搜索历史：同一用户、关键词和搜索类型只保留一条。
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行程搜索历史';

CREATE TABLE IF NOT EXISTS trip_consultation_request (
                                                         id BIGINT NOT NULL comment '记录主键',
                                                         trip_id BIGINT NOT NULL comment '行程ID',
                                                         trip_title VARCHAR(128) NOT NULL comment '行程标题',
                                                         sender_user_id BIGINT NOT NULL comment '发送人用户ID',
                                                         receiver_user_id BIGINT NOT NULL comment '接收方用户ID',
                                                         content VARCHAR(500) NOT NULL comment '正文内容',
                                                         request_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' comment '请求状态',
                                                         created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                         updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                         PRIMARY KEY (id),
                                                         UNIQUE KEY uk_trip_consult_pending (trip_id, sender_user_id, request_status),
                                                         KEY idx_trip_consult_receiver (receiver_user_id, request_status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程咨询请求表';

-- ============================================================================
-- storage-module
-- source: storage-module/src/main/resources/db/storage-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS file_storage (
                                            id BIGINT NOT NULL comment '记录主键',
                                            bucket VARCHAR(128) NOT NULL comment '存储桶',
                                            object_key VARCHAR(512) NOT NULL comment '对象标识或存储Key',
                                            original_file_name VARCHAR(255) NOT NULL comment '原始文件名称',
                                            content_type VARCHAR(128) NOT NULL comment '内容类型',
                                            file_size BIGINT NOT NULL comment '文件SIZE',
                                            biz_type VARCHAR(64) NOT NULL comment '关联业务类型',
                                            biz_id VARCHAR(128) NULL comment '关联业务单据标识',
                                            user_id BIGINT NULL comment '平台用户ID',
                                            storage_type VARCHAR(32) NOT NULL DEFAULT 'MINIO' comment '存储类型',
                                            upload_status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED' comment '上传状态',
                                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                            deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                            PRIMARY KEY (id),
                                            UNIQUE KEY uk_file_object (bucket, object_key, deleted),
                                            KEY idx_file_biz (biz_type, biz_id, deleted),
                                            KEY idx_file_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='文件存储表';

-- ============================================================================
-- driver-track-module
-- source: driver-track-module/src/main/resources/db/driver-track-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS driver_track_record (
                                                   id BIGINT PRIMARY KEY comment '记录主键',
                                                   trip_id BIGINT NOT NULL comment '行程ID',
                                                   driver_id BIGINT NOT NULL comment '驾驶人ID',
                                                   longitude DECIMAL(10,6) NOT NULL comment '经度坐标',
                                                   latitude DECIMAL(10,6) NOT NULL comment '纬度坐标',
                                                   altitude DECIMAL(10,2) NULL comment '海拔',
                                                   speed DECIMAL(10,2) NULL comment '速度',
                                                   direction DECIMAL(10,2) NULL comment '方向',
                                                   accuracy DECIMAL(10,2) NOT NULL comment '定位精度',
                                                   raw_distance_from_prev INT NOT NULL DEFAULT 0 comment '原始距离FROM上一点',
                                                   distance_from_prev INT NOT NULL DEFAULT 0 comment '距离FROM上一点',
                                                   calculated_speed_kmh DECIMAL(10,2) NOT NULL DEFAULT 0 comment '计算完成速度公里每小时',
                                                   provider VARCHAR(16) NOT NULL DEFAULT 'fused' comment '数据或服务提供方',
                                                   app_state VARCHAR(16) NOT NULL DEFAULT 'foreground' comment '应用状态',
                                                   battery_level INT NULL comment '电量等级',
                                                   device_id VARCHAR(128) NULL comment '设备ID',
                                                   sequence_no BIGINT NOT NULL comment '序号编号',
                                                   mock_location TINYINT NOT NULL DEFAULT 0 comment '是否疑似模拟定位：0否、1是',
                                                   point_status VARCHAR(32) NOT NULL DEFAULT 'ACCEPTED' comment '轨迹点状态',
                                                   valid_point TINYINT NOT NULL DEFAULT 1 comment '是否为有效轨迹点：0否、1是',
                                                   risk_score INT NOT NULL DEFAULT 0 comment '风险评分',
                                                   risk_flags VARCHAR(255) NULL comment '风险标记集合',
                                                   reject_reason VARCHAR(255) NULL comment '驳回原因',
                                                   record_time DATETIME NOT NULL comment '记录时间',
                                                   client_send_time DATETIME NULL comment '客户端发送时间',
                                                   server_receive_time DATETIME NOT NULL comment 'SERVERRECEIVE时间',
                                                   created_at DATETIME NOT NULL comment '记录创建时间',
                                                   deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                   UNIQUE KEY uk_driver_track_sequence (trip_id, driver_id, sequence_no, deleted),
                                                   KEY idx_driver_track_trip_time (trip_id, record_time),
                                                   KEY idx_driver_track_driver_time (driver_id, record_time),
                                                   KEY idx_driver_track_status (trip_id, point_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='驾驶人轨迹记录表';

CREATE TABLE IF NOT EXISTS driver_track_distance_record (
                                                            id BIGINT PRIMARY KEY comment '记录主键',
                                                            trip_id BIGINT NOT NULL comment '行程ID',
                                                            driver_id BIGINT NOT NULL comment '驾驶人ID',
                                                            total_distance INT NOT NULL DEFAULT 0 comment '总计距离',
                                                            last_settle_distance INT NOT NULL DEFAULT 0 comment '最后结算距离',
                                                            settle_type VARCHAR(32) NOT NULL comment '结算类型',
                                                            settle_key VARCHAR(128) NOT NULL comment '结算标识或存储Key',
                                                            settle_time DATETIME NOT NULL comment '结算时间',
                                                            event_published TINYINT NOT NULL DEFAULT 0 comment '相关业务事件是否已发布：0否、1是',
                                                            created_at DATETIME NOT NULL comment '记录创建时间',
                                                            updated_at DATETIME NOT NULL comment '记录最后更新时间',
                                                            deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                            UNIQUE KEY uk_driver_track_distance_settle_key (settle_key, deleted),
                                                            KEY idx_driver_track_distance_trip_driver (trip_id, driver_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='驾驶人轨迹距离记录表';

CREATE TABLE IF NOT EXISTS driver_track_deviation_record (
                                                             id BIGINT PRIMARY KEY comment '记录主键',
                                                             trip_id BIGINT NOT NULL comment '行程ID',
                                                             driver_id BIGINT NOT NULL comment '驾驶人ID',
                                                             longitude DECIMAL(10,6) NOT NULL comment '经度坐标',
                                                             latitude DECIMAL(10,6) NOT NULL comment '纬度坐标',
                                                             deviation_distance INT NOT NULL DEFAULT 0 comment '偏离距离',
                                                             deviation_status TINYINT NOT NULL DEFAULT 0 comment '偏离状态',
                                                             record_time DATETIME NOT NULL comment '记录时间',
                                                             created_at DATETIME NOT NULL comment '记录创建时间',
                                                             deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                             KEY idx_driver_track_deviation_trip_driver_time (trip_id, driver_id, record_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='驾驶人轨迹偏离记录表';

CREATE TABLE IF NOT EXISTS trip_track_summary (
                                                  id BIGINT PRIMARY KEY comment '记录主键',
                                                  trip_id BIGINT NOT NULL comment '行程ID',
                                                  primary_user_id BIGINT NOT NULL comment '主要用户ID',
                                                  raw_distance_meters INT NOT NULL DEFAULT 0 comment '原始距离，单位为米',
                                                  filtered_distance_meters INT NOT NULL DEFAULT 0 comment '过滤后距离，单位为米',
                                                  approved_distance_meters INT NOT NULL DEFAULT 0 comment '审核认可距离，单位为米',
                                                  total_point_count INT NOT NULL DEFAULT 0 comment '总计轨迹点数量',
                                                  valid_point_count INT NOT NULL DEFAULT 0 comment '有效轨迹点数量',
                                                  invalid_point_count INT NOT NULL DEFAULT 0 comment '无效轨迹点数量',
                                                  location_gap_count INT NOT NULL DEFAULT 0 comment '位置差值数量',
                                                  warning_count INT NOT NULL DEFAULT 0 comment '警告数量',
                                                  hard_anomaly_count INT NOT NULL DEFAULT 0 comment '严重异常数量',
                                                  risk_score INT NOT NULL DEFAULT 0 comment '风险评分',
                                                  risk_level VARCHAR(16) NOT NULL DEFAULT 'LOW' comment '风险等级',
                                                  settlement_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' comment '结算状态',
                                                  review_reason VARCHAR(255) NULL comment 'REVIEW原因',
                                                  reviewer_id BIGINT NULL comment '审核人ID',
                                                  reviewed_at DATETIME NULL comment '审核时间',
                                                  created_at DATETIME NOT NULL comment '记录创建时间',
                                                  updated_at DATETIME NOT NULL comment '记录最后更新时间',
                                                  deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                  UNIQUE KEY uk_trip_track_summary_trip (trip_id, deleted),
                                                  KEY idx_trip_track_summary_risk (risk_level, settlement_status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程轨迹汇总表';

CREATE TABLE IF NOT EXISTS trip_track_anomaly (
                                                  id BIGINT PRIMARY KEY comment '记录主键',
                                                  trip_id BIGINT NOT NULL comment '行程ID',
                                                  user_id BIGINT NOT NULL comment '平台用户ID',
                                                  previous_point_id BIGINT NULL comment '上一点轨迹点ID',
                                                  current_point_id BIGINT NULL comment '当前轨迹点ID',
                                                  anomaly_type VARCHAR(64) NOT NULL comment '异常类型',
                                                  risk_score INT NOT NULL DEFAULT 0 comment '风险评分',
                                                  detail_json JSON NULL comment '业务详情JSON数据',
                                                  occurred_at DATETIME NOT NULL comment '发生时间',
                                                  created_at DATETIME NOT NULL comment '记录创建时间',
                                                  KEY idx_trip_track_anomaly_trip_time (trip_id, occurred_at),
                                                  KEY idx_trip_track_anomaly_user_time (user_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程轨迹异常表';

CREATE TABLE IF NOT EXISTS trip_member_distance_alert (
                                                          id BIGINT PRIMARY KEY comment '记录主键',
                                                          trip_id BIGINT NOT NULL comment '关联行程ID',
                                                          captain_user_id BIGINT NOT NULL comment '行程队长用户ID',
                                                          member_user_id BIGINT NOT NULL comment '行程成员用户ID',
                                                          alert_level VARCHAR(24) NOT NULL comment '成员距离告警等级',
                                                          distance_m INT NOT NULL comment '成员距离，单位为米',
                                                          started_at DATETIME NOT NULL comment '告警开始时间',
                                                          notified_at DATETIME NULL comment '告警通知时间',
                                                          recovered_at DATETIME NULL comment '距离恢复正常时间',
                                                          acknowledged_at DATETIME NULL comment '告警确认时间',
                                                          handled_action VARCHAR(24) NULL comment '队长处理动作：IGNORE、REMOVE、WAIT、CONTINUE',
                                                          handled_by_user_id BIGINT NULL comment '处理人用户ID',
                                                          handled_at DATETIME NULL comment '处理时间',
                                                          created_at DATETIME NOT NULL comment '记录创建时间',
                                                          updated_at DATETIME NOT NULL comment '记录最后更新时间',
                                                          deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                          KEY idx_trip_member_alert_active (trip_id, member_user_id, recovered_at, deleted),
                                                          KEY idx_trip_member_alert_level (trip_id, alert_level, recovered_at, handled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='trip_member_distance_alert业务表';

CREATE TABLE IF NOT EXISTS trip_execution (
                                              id BIGINT PRIMARY KEY comment '记录主键',
                                              trip_id BIGINT NOT NULL comment '行程ID',
                                              captain_user_id BIGINT NOT NULL comment 'CAPTAIN用户ID',
                                              status VARCHAR(32) NOT NULL comment '业务状态',
                                              planned_distance_m INT NOT NULL DEFAULT 0 comment 'PLANNED距离，单位为米',
                                              raw_gps_distance_m INT NOT NULL DEFAULT 0 comment '原始GPS距离，单位为米',
                                              matched_road_distance_m INT NOT NULL DEFAULT 0 comment '命中道路距离，单位为米',
                                              estimated_gap_distance_m INT NOT NULL DEFAULT 0 comment '估算差值距离，单位为米',
                                              settlement_distance_m INT NOT NULL DEFAULT 0 comment '结算距离，单位为米',
                                              started_at DATETIME NULL comment 'STARTED时间',
                                              ended_at DATETIME NULL comment 'ENDED时间',
                                              created_at DATETIME NOT NULL comment '记录创建时间',
                                              updated_at DATETIME NOT NULL comment '记录最后更新时间',
                                              deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                              UNIQUE KEY uk_trip_execution_trip (trip_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程EXECUTION表';

CREATE TABLE IF NOT EXISTS trip_execution_member (
                                                     id BIGINT PRIMARY KEY comment '记录主键',
                                                     execution_id BIGINT NOT NULL comment 'EXECUTIONID',
                                                     trip_id BIGINT NOT NULL comment '行程ID',
                                                     user_id BIGINT NOT NULL comment '平台用户ID',
                                                     member_role VARCHAR(24) NOT NULL comment '成员角色',
                                                     member_status VARCHAR(32) NOT NULL comment '成员状态',
                                                     ready_at DATETIME NULL comment '准备时间',
                                                     joined_execution_at DATETIME NULL comment '加入EXECUTION时间',
                                                     left_at DATETIME NULL comment '离开时间',
                                                     eligible_flag TINYINT NOT NULL DEFAULT 0 comment '是否符合条件：0否、1是',
                                                     ineligible_reason VARCHAR(128) NULL comment '不符合条件原因',
                                                     created_at DATETIME NOT NULL comment '记录创建时间',
                                                     updated_at DATETIME NOT NULL comment '记录最后更新时间',
                                                     deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                     UNIQUE KEY uk_execution_member (execution_id, user_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程EXECUTION成员表';

CREATE TABLE IF NOT EXISTS trip_track_point (
                                                id BIGINT PRIMARY KEY comment '记录主键',
                                                execution_id BIGINT NOT NULL comment 'EXECUTIONID',
                                                trip_id BIGINT NOT NULL comment '行程ID',
                                                user_id BIGINT NOT NULL comment '平台用户ID',
                                                device_id VARCHAR(128) NULL comment '设备ID',
                                                sequence_no BIGINT NULL comment '序号编号',
                                                longitude DECIMAL(10,6) NOT NULL comment '经度坐标',
                                                latitude DECIMAL(10,6) NOT NULL comment '纬度坐标',
                                                altitude DECIMAL(10,2) NULL comment '海拔',
                                                accuracy DECIMAL(10,2) NULL comment '定位精度',
                                                speed DECIMAL(10,2) NULL comment '速度',
                                                bearing DECIMAL(10,2) NULL comment '方向角',
                                                provider VARCHAR(16) NOT NULL DEFAULT 'fused' comment '数据或服务提供方',
                                                app_state VARCHAR(16) NOT NULL DEFAULT 'foreground' comment '应用状态',
                                                battery_level INT NULL comment '电量等级',
                                                located_at DATETIME NOT NULL comment '定位时间',
                                                client_send_time DATETIME NULL comment '客户端发送时间',
                                                server_receive_time DATETIME NULL comment 'SERVERRECEIVE时间',
                                                mock_location TINYINT NOT NULL DEFAULT 0 comment '是否疑似模拟定位：0否、1是',
                                                point_status VARCHAR(32) NOT NULL comment '轨迹点状态',
                                                valid_point TINYINT NOT NULL DEFAULT 1 comment '是否为有效轨迹点：0否、1是',
                                                risk_score INT NOT NULL DEFAULT 0 comment '风险评分',
                                                risk_flags VARCHAR(255) NULL comment '风险标记集合',
                                                reject_reason VARCHAR(255) NULL comment '驳回原因',
                                                calculated_speed_kmh DECIMAL(10,2) NOT NULL DEFAULT 0 comment '计算完成速度公里每小时',
                                                raw_distance_from_previous_m INT NOT NULL DEFAULT 0 comment '原始距离FROM上一点，单位为米',
                                                distance_from_previous_m INT NOT NULL DEFAULT 0 comment '距离FROM上一点，单位为米',
                                                created_at DATETIME NOT NULL comment '记录创建时间',
                                                deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                UNIQUE KEY uk_track_device_sequence (execution_id, user_id, device_id, sequence_no),
                                                KEY idx_track_execution_time (execution_id, located_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程轨迹轨迹点表';

CREATE TABLE IF NOT EXISTS trip_route_plan_version (
                                                       id BIGINT NOT NULL PRIMARY KEY comment '记录主键',
                                                       execution_id BIGINT NOT NULL comment '行程执行记录ID',
                                                       trip_id BIGINT NOT NULL comment '关联行程ID',
                                                       version_no INT NOT NULL comment '路线规划版本号',
                                                       route_polyline LONGTEXT NOT NULL comment '路线折线编码数据',
                                                       planned_distance_m INT NOT NULL DEFAULT 0 comment '计划路线距离，单位为米',
                                                       required_waypoints_json JSON NULL comment '必须经过的途经点列表JSON数据',
                                                       effective_at DATETIME NOT NULL comment '路线版本生效时间',
                                                       created_by BIGINT NOT NULL comment '路线版本创建人用户ID',
                                                       created_at DATETIME NOT NULL comment '记录创建时间',
                                                       deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                       UNIQUE KEY uk_execution_route_version (execution_id, version_no, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='trip_route_plan_version业务表';

CREATE TABLE IF NOT EXISTS trip_waypoint_arrival (
                                                     id BIGINT PRIMARY KEY comment '记录主键',
                                                     execution_id BIGINT NOT NULL comment 'EXECUTIONID',
                                                     trip_id BIGINT NOT NULL comment '行程ID',
                                                     waypoint_id BIGINT NULL comment '途经点ID',
                                                     arrival_type VARCHAR(24) NOT NULL DEFAULT 'WAYPOINT' comment 'ARRIVAL类型',
                                                     user_id BIGINT NOT NULL comment '平台用户ID',
                                                     first_inside_at DATETIME NOT NULL comment 'FIRST进入范围时间',
                                                     confirmed_at DATETIME NOT NULL comment 'CONFIRMED时间',
                                                     evidence_point_count INT NOT NULL comment '证据轨迹点数量',
                                                     distance_m INT NOT NULL comment '距离，单位为米',
                                                     created_at DATETIME NOT NULL comment '记录创建时间',
                                                     deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                     UNIQUE KEY uk_execution_waypoint_arrival (execution_id, waypoint_id, arrival_type, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程途经点ARRIVAL表';

CREATE TABLE IF NOT EXISTS trip_track_source_switch (
                                                        id BIGINT NOT NULL PRIMARY KEY comment '记录主键',
                                                        execution_id BIGINT NOT NULL comment '行程执行记录ID',
                                                        from_user_id BIGINT NULL comment '切换前轨迹来源用户ID',
                                                        to_user_id BIGINT NOT NULL comment '切换后轨迹来源用户ID',
                                                        switch_reason VARCHAR(64) NOT NULL comment '轨迹来源切换原因',
                                                        switched_at DATETIME NOT NULL comment '轨迹来源切换时间',
                                                        created_at DATETIME NOT NULL comment '记录创建时间',
                                                        deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                        KEY idx_track_source_execution (execution_id, switched_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='trip_track_source_switch业务表';

CREATE TABLE IF NOT EXISTS trip_mileage_settlement (
                                                       id BIGINT PRIMARY KEY comment '记录主键',
                                                       trip_id BIGINT NOT NULL comment '行程ID',
                                                       raw_gps_distance_m INT NOT NULL DEFAULT 0 comment '原始GPS距离，单位为米',
                                                       matched_road_distance_m INT NOT NULL DEFAULT 0 comment '命中道路距离，单位为米',
                                                       estimated_gap_distance_m INT NOT NULL DEFAULT 0 comment '估算差值距离，单位为米',
                                                       settlement_distance_m INT NOT NULL DEFAULT 0 comment '结算距离，单位为米',
                                                       track_coverage_rate INT NOT NULL DEFAULT 0 comment '轨迹覆盖率比例',
                                                       estimated_ratio INT NOT NULL DEFAULT 0 comment '估算比例',
                                                       quality_status VARCHAR(32) NOT NULL comment '质量状态',
                                                       settlement_status VARCHAR(32) NOT NULL comment '结算状态',
                                                       growth_value INT NOT NULL DEFAULT 0 comment '成长值值',
                                                       reason VARCHAR(255) NULL comment '原因说明',
                                                       settled_at DATETIME NULL comment 'SETTLED时间',
                                                       created_at DATETIME NOT NULL comment '记录创建时间',
                                                       updated_at DATETIME NOT NULL comment '记录最后更新时间',
                                                       deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                       UNIQUE KEY uk_trip_mileage_settlement (trip_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程里程结算表';


-- ============================================================================
-- merchant-module
-- source: merchant-module/src/main/resources/db/merchant-schema.sql
-- ============================================================================
CREATE TABLE IF NOT EXISTS merchant_profile (
                                                id BIGINT NOT NULL comment '记录主键',
                                                user_id BIGINT NOT NULL comment '平台用户ID',
                                                merchant_name VARCHAR(128) NOT NULL comment '商家名称',
                                                category VARCHAR(32) NOT NULL comment '分类',
                                                contact_name VARCHAR(64) NOT NULL comment '联系人名称',
                                                contact_phone_cipher VARCHAR(256) NOT NULL comment '联系人手机号加密密文',
                                                contact_phone_mask VARCHAR(32) NOT NULL comment '联系人手机号脱敏展示值',
                                                province_code VARCHAR(16) NOT NULL DEFAULT '' comment 'PROVINCE编码',
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
                                                    pool_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' comment 'POOL状态',
                                                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                    deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                    PRIMARY KEY (id),
                                                    KEY idx_coupon_pool_merchant (merchant_id, pool_status, deleted),
                                                    KEY idx_coupon_pool_source (source_type, pool_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家优惠券POOL表';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家奖励POOL配置表';

CREATE TABLE IF NOT EXISTS merchant_promotion_code (
                                                       id BIGINT NOT NULL comment '记录主键',
                                                       merchant_id BIGINT NOT NULL comment '商家ID',
                                                       promotion_code VARCHAR(64) NOT NULL comment '推广编码',
                                                       channel_name VARCHAR(64) NOT NULL comment 'CHANNEL名称',
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
                                                        stat_date DATE NOT NULL comment 'STAT日期',
                                                        register_count BIGINT NOT NULL DEFAULT 0 comment '注册数量',
                                                        coupon_claim_count BIGINT NOT NULL DEFAULT 0 comment '优惠券CLAIM数量',
                                                        coupon_verify_count BIGINT NOT NULL DEFAULT 0 comment '优惠券VERIFY数量',
                                                        order_count BIGINT NOT NULL DEFAULT 0 comment '订单数量',
                                                        trade_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00 comment 'TRADE金额',
                                                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                        updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                        PRIMARY KEY (id),
                                                        UNIQUE KEY uk_promotion_stats_day (promotion_code_id, stat_date),
                                                        KEY idx_stats_merchant_date (merchant_id, stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家推广STATS表';

CREATE TABLE IF NOT EXISTS merchant_user_relation (
                                                      id BIGINT NOT NULL comment '记录主键',
                                                      merchant_id BIGINT NOT NULL comment '商家ID',
                                                      promotion_code_id BIGINT NOT NULL comment '推广编码ID',
                                                      promotion_code VARCHAR(64) NOT NULL comment '推广编码',
                                                      user_id BIGINT NOT NULL comment '平台用户ID',
                                                      registered_at DATETIME NOT NULL comment 'REGISTERED时间',
                                                      first_consumed_at DATETIME NULL comment 'FIRSTCONSUMED时间',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家审核LOG表';

-- 入驻审核结果与商家主体分离，支持驳回后修改重提并保留审核时间。
CREATE TABLE IF NOT EXISTS merchant_application_review (
                                                           merchant_id BIGINT NOT NULL comment '商家ID',
                                                           reject_reason VARCHAR(255) NOT NULL DEFAULT '' comment '驳回原因',
                                                           reviewer_id BIGINT NULL comment '审核人ID',
                                                           reviewed_at DATETIME NULL comment '审核时间',
                                                           updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                           PRIMARY KEY (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家申请REVIEW表';

-- 普通商户升级为平台合作商的独立申请，不与首次商户认证混用。
CREATE TABLE IF NOT EXISTS merchant_partner_application (
                                                            merchant_id BIGINT NOT NULL comment '商家ID',
                                                            application_reason VARCHAR(500) NOT NULL comment '申请原因',
                                                            cooperation_categories VARCHAR(255) NOT NULL DEFAULT '' comment '合作CATEGORIES',
                                                            planned_monthly_stock INT NOT NULL DEFAULT 0 comment 'PLANNED月度库存',
                                                            application_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' comment '申请状态',
                                                            reject_reason VARCHAR(255) NOT NULL DEFAULT '' comment '驳回原因',
                                                            reviewer_id BIGINT NULL comment '审核人ID',
                                                            reviewed_at DATETIME NULL comment '审核时间',
                                                            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '记录创建时间',
                                                            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                            deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                            PRIMARY KEY (merchant_id),
                                                            KEY idx_partner_application_status (application_status, updated_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家PARTNER申请表';

CREATE TABLE IF NOT EXISTS merchant_partner_cancellation (
                                                             merchant_id BIGINT NOT NULL comment '商家ID',
                                                             cancellation_reason VARCHAR(500) NOT NULL comment 'CANCELLATION原因',
                                                             cancellation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' comment 'CANCELLATION状态',
                                                             reject_reason VARCHAR(255) NOT NULL DEFAULT '' comment '驳回原因',
                                                             reviewer_id BIGINT NULL comment '审核人ID',
                                                             requested_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP comment '申请时间',
                                                             reviewed_at DATETIME NULL comment '审核时间',
                                                             updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '记录最后更新时间',
                                                             deleted TINYINT NOT NULL DEFAULT 0 comment '逻辑删除标记：0未删除、1已删除',
                                                             PRIMARY KEY (merchant_id),
                                                             KEY idx_partner_cancellation_status (cancellation_status, updated_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='商家PARTNERCANCELLATION表';

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
                                              parking_info VARCHAR(255) NOT NULL DEFAULT '' comment 'PARKINGINFO',
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
                                                     sold_count INT NOT NULL DEFAULT 0 comment 'SOLD数量',
                                                     limit_count INT NOT NULL DEFAULT 1 comment '限制数量',
                                                     group_enabled TINYINT NOT NULL DEFAULT 0 comment '是否启用拼团',
                                                     group_people INT NULL comment '拼团人数',
                                                     group_timeout_hours INT NULL comment '拼团TIMEOUT，单位为小时',
                                                     publish_time DATETIME NOT NULL comment '发布时间',
                                                     expire_time DATETIME NOT NULL comment '过期时间',
                                                     use_start_time DATETIME NOT NULL comment 'USE起点时间',
                                                     use_end_time DATETIME NOT NULL comment 'USE终点时间',
                                                     reservation_required TINYINT NOT NULL DEFAULT 0 comment '预约要求',
                                                     refundable TINYINT NOT NULL DEFAULT 1 comment '可退款',
                                                     holiday_available TINYINT NOT NULL DEFAULT 1 comment '节假日可用',
                                                     stackable TINYINT NOT NULL DEFAULT 0 comment '可叠加',
                                                     use_instructions VARCHAR(1000) NOT NULL DEFAULT '' comment 'USE使用说明',
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

-- ============================================================================
-- groupbuy-module
-- source: groupbuy-module/src/main/resources/db/groupbuy-schema.sql
-- ============================================================================
create table if not exists groupbuy_activity (
                                                 id bigint not null comment '记录主键',
                                                 merchant_id bigint not null default 0 comment '商家ID',
                                                 product_id bigint not null comment '商品ID',
                                                 initiator_user_id bigint not null comment '发起人用户ID',
                                                 target_people int not null comment '目标人数',
                                                 current_people int not null default 0 comment '当前人数',
                                                 group_price decimal(12,2) not null default 0.00 comment '拼团价格',
                                                 ladder_price_json text null comment '阶梯价格JSON数据',
                                                 activity_status varchar(32) not null comment '活动状态',
                                                 start_at datetime not null comment '起点时间',
                                                 expire_at datetime not null comment '过期时间',
                                                 success_at datetime null comment '成功时间',
                                                 failed_at datetime null comment '失败时间',
                                                 created_at datetime not null comment '记录创建时间',
                                                 updated_at datetime not null comment '记录最后更新时间',
                                                 deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                 primary key (id),
                                                 key idx_groupbuy_activity_product (product_id, activity_status),
                                                 key idx_groupbuy_activity_merchant (merchant_id, activity_status),
                                                 key idx_groupbuy_activity_expire (activity_status, expire_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='拼团活动表';

create table if not exists groupbuy_participant (
                                                    id bigint not null comment '记录主键',
                                                    activity_id bigint not null comment '活动ID',
                                                    order_id bigint not null comment '订单ID',
                                                    user_id bigint not null comment '平台用户ID',
                                                    participant_status varchar(32) not null comment '参与人状态',
                                                    joined_at datetime not null comment '加入时间',
                                                    paid_at datetime null comment '支付时间',
                                                    refunded_at datetime null comment '退款时间',
                                                    created_at datetime not null comment '记录创建时间',
                                                    updated_at datetime not null comment '记录最后更新时间',
                                                    deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                    primary key (id),
                                                    unique key uk_groupbuy_participant_user (activity_id, user_id, deleted),
                                                    key idx_groupbuy_participant_order (order_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='拼团参与人表';

-- ============================================================================
-- order-module
-- source: order-module/src/main/resources/db/order-schema.sql
-- ============================================================================
create table if not exists order_trade (
                                           id bigint not null comment '记录主键',
                                           order_no varchar(64) not null comment '订单编号',
                                           user_id bigint not null comment '平台用户ID',
                                           merchant_id bigint not null default 0 comment '商家ID',
                                           product_id bigint not null comment '商品ID',
                                           activity_id bigint null comment '活动ID',
                                           original_amount decimal(12,2) not null default 0.00 comment '原始金额',
                                           groupbuy_discount_amount decimal(12,2) not null default 0.00 comment '拼团优惠金额',
                                           coupon_deduction_amount decimal(12,2) not null default 0.00 comment '优惠券抵扣金额',
                                           payable_amount decimal(12,2) not null default 0.00 comment '应付金额',
                                           paid_amount decimal(12,2) not null default 0.00 comment '支付金额',
                                           user_coupon_id bigint null comment '用户优惠券ID',
                                           order_status varchar(32) not null comment '订单状态',
                                           payment_status varchar(32) not null comment '支付状态',
                                           verification_status varchar(32) not null comment '核销状态',
                                           refund_status varchar(32) not null comment '退款状态',
                                           profit_sharing_status varchar(32) not null comment '分账分账状态',
                                           expire_at datetime null comment '过期时间',
                                           paid_at datetime null comment '支付时间',
                                           completed_at datetime null comment '完成时间',
                                           remark varchar(255) not null default '' comment '业务备注',
                                           created_at datetime not null comment '记录创建时间',
                                           updated_at datetime not null comment '记录最后更新时间',
                                           deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                           primary key (id),
                                           unique key uk_order_trade_no (order_no),
                                           key idx_order_trade_user (user_id, created_at),
                                           key idx_order_trade_merchant (merchant_id, created_at),
                                           key idx_order_trade_status (order_status, payment_status),
                                           key idx_order_trade_activity (activity_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='订单TRADE表';

create table if not exists order_item (
                                          id bigint not null comment '记录主键',
                                          order_id bigint not null comment '订单ID',
                                          product_id bigint not null comment '商品ID',
                                          product_name varchar(128) not null comment '商品名称',
                                          product_type varchar(32) not null comment '商品类型',
                                          unit_price decimal(12,2) not null default 0.00 comment 'UNIT价格',
                                          quantity int not null default 1 comment '数量',
                                          total_amount decimal(12,2) not null default 0.00 comment '总计金额',
                                          snapshot_json text null comment '业务快照JSON数据',
                                          created_at datetime not null comment '记录创建时间',
                                          primary key (id),
                                          key idx_order_item_order (order_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='订单事项表';

create table if not exists order_compensation_task (
                                                       id bigint not null comment '记录主键',
                                                       biz_type varchar(64) not null comment '关联业务类型',
                                                       biz_id varchar(64) not null comment '关联业务单据标识',
                                                       idempotent_key varchar(128) not null comment '业务幂等键，用于防止重复处理',
                                                       target_module varchar(64) not null comment '目标模块',
                                                       request_payload text null comment '任务请求参数载荷',
                                                       task_status varchar(32) not null comment '任务状态',
                                                       retry_count int not null default 0 comment '任务已重试次数',
                                                       next_retry_at datetime null comment '下一次计划重试时间',
                                                       last_error varchar(1000) null comment '最后一次执行错误信息',
                                                       created_at datetime not null comment '记录创建时间',
                                                       updated_at datetime not null comment '记录最后更新时间',
                                                       primary key (id),
                                                       key idx_order_compensation_status (task_status, next_retry_at),
                                                       key idx_order_compensation_biz (biz_type, biz_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='订单COMPENSATION任务表';

-- ============================================================================
-- payment-module
-- source: payment-module/src/main/resources/db/payment-schema.sql
-- ============================================================================
create table if not exists payment_record (
                                              id bigint not null comment '记录主键',
                                              order_id bigint not null comment '订单ID',
                                              order_no varchar(64) not null default '' comment '订单编号',
                                              payment_no varchar(64) not null comment '支付编号',
                                              wx_prepay_id varchar(128) null comment '微信预支付ID',
                                              wx_transaction_id varchar(128) null comment '微信交易ID',
                                              pay_channel varchar(32) not null comment 'PAYCHANNEL',
                                              pay_amount decimal(12,2) not null default 0.00 comment 'PAY金额',
                                              payment_status varchar(32) not null comment '支付状态',
                                              callback_payload text null comment '第三方回调原始载荷',
                                              paid_at datetime null comment '支付时间',
                                              created_at datetime not null comment '记录创建时间',
                                              updated_at datetime not null comment '记录最后更新时间',
                                              deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                              primary key (id),
                                              unique key uk_payment_no (payment_no),
                                              unique key uk_payment_wx_transaction (wx_transaction_id),
                                              key idx_payment_order (order_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='支付记录表';

create table if not exists payment_refund_record (
                                                     id bigint not null comment '记录主键',
                                                     order_id bigint not null comment '订单ID',
                                                     refund_no varchar(64) not null comment '退款编号',
                                                     wx_refund_id varchar(128) null comment '微信退款ID',
                                                     user_id bigint not null comment '平台用户ID',
                                                     refund_amount decimal(12,2) not null default 0.00 comment '退款金额',
                                                     refund_reason varchar(255) not null default '' comment '退款原因',
                                                     refund_type varchar(32) not null comment '退款类型',
                                                     refund_status varchar(32) not null comment '退款状态',
                                                     audit_status varchar(32) not null comment '审核状态',
                                                     callback_payload text null comment '第三方回调原始载荷',
                                                     requested_at datetime not null comment '申请时间',
                                                     refunded_at datetime null comment '退款时间',
                                                     created_at datetime not null comment '记录创建时间',
                                                     updated_at datetime not null comment '记录最后更新时间',
                                                     deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                     primary key (id),
                                                     unique key uk_payment_refund_no (refund_no),
                                                     key idx_payment_refund_order (order_id),
                                                     key idx_payment_refund_status (refund_status, audit_status)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='支付退款记录表';

create table if not exists payment_profit_sharing_record (
                                                             id bigint not null comment '记录主键',
                                                             order_id bigint not null comment '订单ID',
                                                             merchant_id bigint not null comment '商家ID',
                                                             verification_id bigint not null comment '核销ID',
                                                             sharing_no varchar(64) not null comment '分账编号',
                                                             wx_sharing_id varchar(128) null comment '微信分账ID',
                                                             total_amount decimal(12,2) not null default 0.00 comment '总计金额',
                                                             platform_commission_amount decimal(12,2) not null default 0.00 comment '平台佣金金额',
                                                             merchant_amount decimal(12,2) not null default 0.00 comment '商家金额',
                                                             commission_rate decimal(5,4) not null default 0.0000 comment '佣金比例',
                                                             sharing_status varchar(32) not null comment '分账状态',
                                                             callback_payload text null comment '第三方回调原始载荷',
                                                             shared_at datetime null comment '分账完成时间',
                                                             created_at datetime not null comment '记录创建时间',
                                                             updated_at datetime not null comment '记录最后更新时间',
                                                             deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                             primary key (id),
                                                             unique key uk_payment_sharing_no (sharing_no),
                                                             key idx_payment_sharing_order (order_id),
                                                             key idx_payment_sharing_merchant (merchant_id, created_at),
                                                             key idx_payment_sharing_status (sharing_status)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='支付分账分账记录表';

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
                                                        id bigint not null comment '记录主键',
                                                        level_code varchar(16) not null comment '等级编码',
                                                        min_score decimal(8,2) not null comment '最低评分',
                                                        max_score decimal(8,2) not null comment '最高评分',
                                                        commission_rate decimal(8,4) not null comment '佣金比例',
                                                        rank_weight decimal(8,4) not null comment '排序权重',
                                                        exclusion_radius_km decimal(8,2) not null default 0.00 comment '排除半径，单位为公里',
                                                        mapping_status varchar(32) not null default 'ACTIVE' comment '映射状态',
                                                        created_at datetime not null comment '记录创建时间',
                                                        updated_at datetime not null comment '记录最后更新时间',
                                                        deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                        primary key (id),
                                                        unique key uk_assessment_level (level_code, deleted),
                                                        key idx_assessment_score_range (min_score, max_score, mapping_status)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='考核等级映射表';

create table if not exists assessment_merchant_score (
                                                         id bigint not null comment '记录主键',
                                                         merchant_id bigint not null comment '商家ID',
                                                         assessment_period varchar(16) not null comment '考核周期',
                                                         total_score decimal(8,2) not null comment '总计评分',
                                                         merchant_level varchar(16) not null comment '商家等级',
                                                         commission_rate decimal(8,4) not null comment '佣金比例',
                                                         rank_weight decimal(8,4) not null comment '排序权重',
                                                         exclusion_radius_km decimal(8,2) not null default 0.00 comment '排除半径，单位为公里',
                                                         calculate_status varchar(32) not null comment '计算状态',
                                                         calculated_at datetime not null comment '计算完成时间',
                                                         request_id varchar(128) not null comment '请求唯一标识，用于链路追踪或幂等控制',
                                                         created_at datetime not null comment '记录创建时间',
                                                         updated_at datetime not null comment '记录最后更新时间',
                                                         deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                         primary key (id),
                                                         unique key uk_assessment_merchant_assessment_period (merchant_id, assessment_period, deleted),
                                                         unique key uk_assessment_request (request_id, deleted),
                                                         key idx_assessment_merchant_latest (merchant_id, calculated_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='考核商家评分表';

create table if not exists assessment_merchant_score_item (
                                                              id bigint not null comment '记录主键',
                                                              score_id bigint not null comment '评分ID',
                                                              metric_key varchar(64) not null comment '指标标识或存储Key',
                                                              metric_value decimal(12,2) not null comment '指标值',
                                                              score_delta decimal(8,2) not null comment '评分调整值',
                                                              created_at datetime not null comment '记录创建时间',
                                                              deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                              primary key (id),
                                                              key idx_assessment_item_score (score_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='商家考核评分明细表';


create table if not exists assessment_manual_adjustment (
                                                            id bigint not null comment '记录主键',
                                                            merchant_id bigint not null comment '商家ID',
                                                            score_delta decimal(8,2) not null comment '评分调整值',
                                                            reason varchar(255) not null comment '原因说明',
                                                            operator_id bigint not null comment '管理员ID',
                                                            request_id varchar(128) not null comment '请求唯一标识，用于链路追踪或幂等控制',
                                                            created_at datetime not null comment '记录创建时间',
                                                            updated_at datetime not null comment '记录最后更新时间',
                                                            deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                            primary key (id),
                                                            unique key uk_assessment_manual_adjustment_request (request_id, deleted),
                                                            key idx_assessment_manual_adjustment_merchant (merchant_id, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='考核人工调整表';

-- ============================================================================
-- customer-service-module
-- source: customer-service-module/src/main/resources/db/customer-service-schema.sql
-- ============================================================================
create table if not exists customer_service_ticket (
                                                       id bigint not null comment '记录主键',
                                                       creator_type varchar(32) not null comment '创建人类型',
                                                       creator_id bigint not null comment '创建人ID',
                                                       scene varchar(64) not null comment '业务场景',
                                                       target_type varchar(64) not null default '' comment '目标类型',
                                                       target_id varchar(64) not null default '' comment '目标ID',
                                                       title varchar(128) not null comment '展示标题',
                                                       content varchar(2048) not null comment '正文内容',
                                                       ticket_status varchar(32) not null default 'OPEN' comment '工单状态',
                                                       priority varchar(32) not null default 'NORMAL' comment '优先级',
                                                       assigned_admin_id bigint null comment '分配后台ID',
                                                       request_id varchar(128) not null comment '请求唯一标识，用于链路追踪或幂等控制',
                                                       created_at datetime not null comment '记录创建时间',
                                                       updated_at datetime not null comment '记录最后更新时间',
                                                       closed_at datetime null comment '关闭时间',
                                                       deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                       primary key (id),
                                                       unique key uk_customer_service_ticket_request (request_id, deleted),
                                                       key idx_customer_service_creator (creator_type, creator_id, created_at),
                                                       key idx_customer_service_status (ticket_status, priority, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='客服工单表';

create table if not exists customer_service_ticket_message (
                                                               id bigint not null comment '记录主键',
                                                               ticket_id bigint not null comment '工单ID',
                                                               sender_type varchar(32) not null comment '发送人类型',
                                                               sender_id bigint not null comment '发送人ID',
                                                               message_type varchar(32) not null comment '消息类型',
                                                               content varchar(2048) not null comment '正文内容',
                                                               image_keys_json text null comment '图片Key列表JSON数据',
                                                               request_id varchar(128) null comment '请求唯一标识，用于链路追踪或幂等控制',
                                                               created_at datetime not null comment '记录创建时间',
                                                               deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                               primary key (id),
                                                               key idx_customer_service_message_ticket (ticket_id, created_at),
                                                               unique key uk_customer_service_message_request (request_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='客服工单消息表';

-- ============================================================================
-- notify-module
-- source: notify-module/src/main/resources/db/notify-schema.sql
-- ============================================================================
create table if not exists notify_message (
                                              id bigint not null comment '记录主键',
                                              receiver_type varchar(32) not null comment '接收方类型',
                                              receiver_id bigint not null comment '接收方ID',
                                              scene varchar(64) not null default 'SYSTEM' comment '业务场景',
                                              event_type varchar(64) not null comment '事件类型',
                                              title varchar(128) not null comment '展示标题',
                                              content varchar(1024) not null comment '正文内容',
                                              target_type varchar(64) not null default '' comment '目标类型',
                                              target_id varchar(64) not null default '' comment '目标ID',
                                              read_status varchar(32) not null default 'UNREAD' comment '已读状态',
                                              read_at datetime null comment '已读时间',
                                              request_id varchar(128) not null comment '请求唯一标识，用于链路追踪或幂等控制',
                                              created_at datetime not null comment '记录创建时间',
                                              updated_at datetime not null comment '记录最后更新时间',
                                              deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                              primary key (id),
                                              unique key uk_notify_message_request (request_id, deleted),
                                              key idx_notify_receiver (receiver_type, receiver_id, read_status, created_at),
                                              key idx_notify_target (target_type, target_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='通知消息表';

create table if not exists notify_template (
                                               id bigint not null comment '记录主键',
                                               template_code varchar(64) not null comment '模板编码',
                                               channel varchar(32) not null comment '通知或投递渠道',
                                               title_template varchar(128) not null comment '标题模板',
                                               content_template varchar(1024) not null comment '内容模板',
                                               template_status varchar(32) not null default 'ACTIVE' comment '模板状态',
                                               created_at datetime not null comment '记录创建时间',
                                               updated_at datetime not null comment '记录最后更新时间',
                                               deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                               primary key (id),
                                               unique key uk_notify_template_code (template_code, channel, deleted)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='通知模板表';

create table if not exists notify_delivery_log (
                                                   id bigint not null comment '记录主键',
                                                   message_id bigint not null comment '消息ID',
                                                   channel varchar(32) not null comment '通知或投递渠道',
                                                   delivery_status varchar(32) not null comment '投递状态',
                                                   third_request_id varchar(128) not null default '' comment '第三方请求ID',
                                                   error_message varchar(512) not null default '' comment '错误信息',
                                                   retry_count int not null default 0 comment '任务已重试次数',
                                                   next_retry_at datetime null comment '下一次计划重试时间',
                                                   created_at datetime not null comment '记录创建时间',
                                                   updated_at datetime not null comment '记录最后更新时间',
                                                   deleted tinyint not null default 0 comment '逻辑删除标记：0未删除、1已删除',
                                                   primary key (id),
                                                   key idx_notify_delivery_message (message_id, channel),
                                                   key idx_notify_delivery_retry (delivery_status, next_retry_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='通知投递日志表';

create table if not exists app_push_device (
                                               id bigint primary key comment '记录主键',
                                               user_id bigint not null comment '平台用户ID',
                                               device_id varchar(128) not null comment '设备唯一标识',
                                               platform varchar(16) not null comment 'ANDROID、IOS',
                                               vendor varchar(32) not null default 'GENERIC' comment '推送厂商：FCM、HUAWEI、XIAOMI、OPPO、VIVO、APNS、GENERIC',
                                               push_token varchar(512) not null comment '系统推送设备Token',
                                               app_version varchar(32) null comment 'App版本',
                                               enabled tinyint(1) not null default 1 comment '是否启用',
                                               last_seen_at datetime not null comment '最近活跃时间',
                                               created_at datetime not null comment '创建时间',
                                               updated_at datetime not null comment '更新时间',
                                               deleted tinyint(1) not null default 0 comment '逻辑删除',
                                               unique key uk_push_device (user_id, device_id, deleted),
                                               key idx_push_device_user_enabled (user_id, enabled, updated_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='App系统推送设备表';

create table if not exists app_push_task (
                                             id bigint primary key comment '记录主键',
                                             user_id bigint not null comment '接收用户ID',
                                             event_type varchar(64) not null comment '事件类型',
                                             title varchar(128) not null comment '推送标题',
                                             content varchar(512) not null comment '推送内容',
                                             payload_json json null comment '客户端跳转参数',
                                             idempotency_key varchar(128) not null comment '幂等键',
                                             delivery_status varchar(24) not null default 'PENDING' comment 'PENDING、SENT、FAILED、SKIPPED',
                                             retry_count int not null default 0 comment '重试次数',
                                             next_retry_at datetime null comment '下次重试时间',
                                             last_error varchar(512) null comment '最后错误',
                                             created_at datetime not null comment '创建时间',
                                             updated_at datetime not null comment '更新时间',
                                             deleted tinyint(1) not null default 0 comment '逻辑删除',
                                             unique key uk_push_task_idempotency (idempotency_key, deleted),
                                             key idx_push_task_delivery (delivery_status, next_retry_at, created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci comment='App系统推送任务表';

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

-- ============================================================================
-- 行程推荐队长评分汇总
-- ============================================================================
CREATE TABLE IF NOT EXISTS trip_leader_rating_summary (
                                                          leader_user_id BIGINT NOT NULL comment '队长用户ID',
                                                          rating DECIMAL(3,2) NOT NULL DEFAULT 5.00 comment '队长综合评分，范围0~5',
                                                          positive_rate DECIMAL(5,4) NOT NULL DEFAULT 1.0000 comment '好评率，范围0~1',
                                                          rating_count INT NOT NULL DEFAULT 0 comment '有效评价数量',
                                                          updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP comment '汇总更新时间',
                                                          deleted TINYINT(1) NOT NULL DEFAULT 0 comment '逻辑删除标记',
                                                          PRIMARY KEY (leader_user_id),
                                                          KEY idx_trip_leader_rating (rating, positive_rate)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 comment='行程队长评分汇总表';


SET FOREIGN_KEY_CHECKS = 1;
-- 建表完成。
