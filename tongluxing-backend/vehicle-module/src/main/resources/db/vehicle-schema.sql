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
    key idx_vehicle_cert_status (status)
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

-- Compatibility migration for databases created by the early prototype schema.
-- MySQL does not offer a portable ADD COLUMN IF NOT EXISTS across all supported
-- versions, therefore each addition is guarded through information_schema.
set @ddl = if(
    (select count(*) from information_schema.columns
     where table_schema = database() and table_name = 'vehicle_certification' and column_name = 'vehicle_type') = 0,
    'alter table vehicle_certification add column vehicle_type varchar(32) not null default '''' after plate_no_mask',
    'select 1'
);
prepare vehicle_schema_stmt from @ddl;
execute vehicle_schema_stmt;
deallocate prepare vehicle_schema_stmt;

-- The prototype schema used one mandatory license_image_key column. The
-- current model stores front/back URLs separately, so keep the legacy column
-- readable while allowing new rows to omit it.
set @ddl = if(
    (select count(*) from information_schema.columns
     where table_schema = database() and table_name = 'vehicle_certification'
       and column_name = 'license_image_key' and is_nullable = 'NO') > 0,
    'alter table vehicle_certification modify column license_image_key varchar(512) null',
    'select 1'
);
prepare vehicle_schema_stmt from @ddl;
execute vehicle_schema_stmt;
deallocate prepare vehicle_schema_stmt;

set @ddl = if(
    (select count(*) from information_schema.columns
     where table_schema = database() and table_name = 'vehicle_certification' and column_name = 'engine_no_cipher') = 0,
    'alter table vehicle_certification add column engine_no_cipher varchar(256) null after vin_mask',
    'select 1'
);
prepare vehicle_schema_stmt from @ddl;
execute vehicle_schema_stmt;
deallocate prepare vehicle_schema_stmt;

set @ddl = if(
    (select count(*) from information_schema.columns
     where table_schema = database() and table_name = 'vehicle_certification' and column_name = 'register_date') = 0,
    'alter table vehicle_certification add column register_date date null after engine_no_mask',
    'select 1'
);
prepare vehicle_schema_stmt from @ddl;
execute vehicle_schema_stmt;
deallocate prepare vehicle_schema_stmt;

set @ddl = if(
    (select count(*) from information_schema.columns
     where table_schema = database() and table_name = 'vehicle_certification' and column_name = 'issue_date') = 0,
    'alter table vehicle_certification add column issue_date date null after register_date',
    'select 1'
);
prepare vehicle_schema_stmt from @ddl;
execute vehicle_schema_stmt;
deallocate prepare vehicle_schema_stmt;

set @ddl = if(
    (select count(*) from information_schema.columns
     where table_schema = database() and table_name = 'vehicle_certification' and column_name = 'issuing_authority') = 0,
    'alter table vehicle_certification add column issuing_authority varchar(128) null after issue_date',
    'select 1'
);
prepare vehicle_schema_stmt from @ddl;
execute vehicle_schema_stmt;
deallocate prepare vehicle_schema_stmt;

set @ddl = if(
    (select count(*) from information_schema.columns
     where table_schema = database() and table_name = 'vehicle_certification' and column_name = 'license_front_image_key') = 0,
    'alter table vehicle_certification add column license_front_image_key varchar(512) not null default '''' after issuing_authority',
    'select 1'
);
prepare vehicle_schema_stmt from @ddl;
execute vehicle_schema_stmt;
deallocate prepare vehicle_schema_stmt;

set @ddl = if(
    (select count(*) from information_schema.columns
     where table_schema = database() and table_name = 'vehicle_certification' and column_name = 'license_back_image_key') = 0,
    'alter table vehicle_certification add column license_back_image_key varchar(512) null after license_front_image_key',
    'select 1'
);
prepare vehicle_schema_stmt from @ddl;
execute vehicle_schema_stmt;
deallocate prepare vehicle_schema_stmt;

set @ddl = if(
    (select count(*) from information_schema.columns
     where table_schema = database() and table_name = 'vehicle_certification' and column_name = 'recognition_source') = 0,
    'alter table vehicle_certification add column recognition_source varchar(32) not null default ''MINIPROGRAM_OCR'' after license_back_image_key',
    'select 1'
);
prepare vehicle_schema_stmt from @ddl;
execute vehicle_schema_stmt;
deallocate prepare vehicle_schema_stmt;
