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
