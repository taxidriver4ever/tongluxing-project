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
    action_type varchar(32) not null comment 'login/password_login/wx_phone_login/app_login/app_bind_login/logout/refresh',
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

-- App 小闭环联调账号：13888888888 / 12345678。
-- 密码只保存 BCrypt hash，不在数据库保存明文。
insert ignore into auth_account
    (id, user_id, phone, account_status, created_at, updated_at, deleted)
values
    (900000000000000001, 900000000000000101, '13888888888', 1, now(), now(), 0);

insert ignore into auth_password_credential
    (id, user_id, password_hash, password_version, password_status,
     last_set_time, created_at, updated_at, deleted)
select
    900000000000000002, user_id,
    '$2a$10$csNtGZEuy.EdI9XA9ql8tu2PfMbncIKGsQ49QKFC2ACXLlFcPUGg.',
    'BCRYPT', 1, now(), now(), now(), 0
from auth_account
where phone = '13888888888' and deleted = 0
limit 1;
