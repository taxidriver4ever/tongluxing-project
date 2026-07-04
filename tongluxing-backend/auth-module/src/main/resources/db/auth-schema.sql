create table if not exists auth_account (
    id bigint primary key,
    user_id bigint not null,
    phone varchar(20) not null,
    account_status tinyint not null default 1 comment '1 normal, 2 disabled',
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
    action_type varchar(32) not null comment 'login/logout/refresh',
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
