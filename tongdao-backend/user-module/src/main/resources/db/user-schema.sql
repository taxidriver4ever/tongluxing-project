create table if not exists user_profile (
    id bigint primary key,
    user_id bigint not null unique,
    nickname varchar(32) not null default '',
    avatar_image_key varchar(512) not null default '',
    gender tinyint not null default 0,
    birthday date null,
    city_code varchar(32) not null default '',
    city_name varchar(64) not null default '',
    bio varchar(160) not null default '',
    profile_completion tinyint not null default 0,
    real_name_status varchar(20) not null default 'UNSUBMITTED',
    created_at datetime not null,
    updated_at datetime not null,
    deleted tinyint not null default 0,
    key idx_user_profile_city (city_code),
    key idx_user_profile_status (real_name_status)
);

create table if not exists user_identity_certification (
    id bigint primary key,
    user_id bigint not null,
    real_name varchar(64) not null,
    id_card_no_cipher varchar(256) not null,
    id_card_no_mask varchar(32) not null,
    face_image_key varchar(512) not null default '',
    status varchar(20) not null default 'PENDING',
    reject_reason varchar(255) not null default '',
    submitted_at datetime not null,
    reviewed_by bigint null,
    reviewed_at datetime null,
    created_at datetime not null,
    updated_at datetime not null,
    key idx_user_identity_user (user_id),
    key idx_user_identity_status (status)
);

create table if not exists user_privacy_setting (
    id bigint primary key,
    user_id bigint not null unique,
    profile_visible tinyint not null default 1,
    phone_visible tinyint not null default 0,
    trip_visible tinyint not null default 1,
    location_visible tinyint not null default 1,
    allow_team_invite tinyint not null default 1,
    allow_private_message tinyint not null default 1,
    created_at datetime not null,
    updated_at datetime not null
);

create table if not exists user_emergency_contact (
    id bigint primary key,
    user_id bigint not null,
    contact_name varchar(64) not null,
    relation varchar(32) not null default '',
    phone_cipher varchar(256) not null,
    phone_mask varchar(32) not null,
    is_default tinyint not null default 0,
    created_at datetime not null,
    updated_at datetime not null,
    deleted tinyint not null default 0,
    key idx_user_emergency_contact_user (user_id)
);

create table if not exists user_profile_audit_log (
    id bigint primary key,
    user_id bigint not null,
    biz_type varchar(32) not null,
    before_json text null,
    after_json text null,
    operator_id bigint null,
    operator_type varchar(20) not null,
    created_at datetime not null,
    key idx_user_profile_audit_user (user_id),
    key idx_user_profile_audit_biz (biz_type)
);
