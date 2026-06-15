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
    vin_cipher varchar(256) null,
    vin_mask varchar(32) null,
    engine_no_mask varchar(32) null,
    license_image_key varchar(512) not null,
    status varchar(32) not null default 'PENDING',
    reject_reason varchar(255) not null default '',
    submitted_at datetime not null,
    reviewed_at datetime null,
    reviewer_id bigint null,
    key idx_vehicle_cert_vehicle (vehicle_id, submitted_at),
    key idx_vehicle_cert_user (user_id),
    key idx_vehicle_cert_status (status)
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
