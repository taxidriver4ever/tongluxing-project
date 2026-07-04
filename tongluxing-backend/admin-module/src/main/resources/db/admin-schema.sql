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
