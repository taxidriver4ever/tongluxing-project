create table if not exists user_profile (
    id bigint not null,
    user_id bigint not null,
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
    unique key uk_user_profile_user (user_id, deleted)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists user_identity_certification (
    id bigint not null,
    user_id bigint not null,
    real_name_cipher varchar(256) not null,
    id_card_no_cipher varchar(512) not null,
    driving_license_image_key varchar(512) not null,
    face_image_key varchar(512) not null,
    certification_status varchar(20) not null default 'PENDING',
    reject_reason varchar(255) null,
    submitted_at datetime not null,
    reviewed_at datetime null,
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    primary key (id),
    key idx_user_cert_user_status (user_id, certification_status, submitted_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists user_privacy_setting (
    id bigint not null,
    user_id bigint not null,
    profile_visibility varchar(16) not null default 'PUBLIC',
    vehicle_visibility varchar(16) not null default 'TEAM_ONLY',
    invite_enabled_flag tinyint not null default 1,
    created_at datetime not null default current_timestamp,
    updated_at datetime not null default current_timestamp on update current_timestamp,
    deleted tinyint not null default 0,
    primary key (id),
    unique key uk_user_privacy_user (user_id, deleted)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
