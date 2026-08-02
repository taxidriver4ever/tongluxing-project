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
