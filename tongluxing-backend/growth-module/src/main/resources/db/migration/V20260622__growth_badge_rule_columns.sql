-- Add relational rule columns when they do not exist.
set @sql = if(
    exists(
        select 1
        from information_schema.columns
        where table_schema = database()
          and table_name = 'growth_badge'
          and column_name = 'event_type'
    ),
    'select 1',
    'alter table growth_badge add column event_type varchar(32) null after badge_image_key'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = if(
    exists(
        select 1
        from information_schema.columns
        where table_schema = database()
          and table_name = 'growth_badge'
          and column_name = 'threshold'
    ),
    'select 1',
    'alter table growth_badge add column threshold int null after event_type'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- Migrate the two fixed properties from the legacy JSON column.
set @sql = if(
    exists(
        select 1
        from information_schema.columns
        where table_schema = database()
          and table_name = 'growth_badge'
          and column_name = 'condition_json'
    ),
    'update growth_badge
     set event_type = coalesce(event_type, condition_json ->> ''$.eventType''),
         threshold = coalesce(threshold, condition_json ->> ''$.threshold'')
     where event_type is null or threshold is null',
    'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- Only enforce NOT NULL and remove JSON after every existing rule was migrated.
set @unmigrated = (
    select count(*)
    from growth_badge
    where event_type is null
       or threshold is null
);

set @sql = if(
    @unmigrated = 0,
    'alter table growth_badge modify column event_type varchar(32) not null, modify column threshold int not null',
    'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = if(
    @unmigrated = 0
    and exists(
        select 1
        from information_schema.columns
        where table_schema = database()
          and table_name = 'growth_badge'
          and column_name = 'condition_json'
    ),
    'alter table growth_badge drop column condition_json',
    'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = if(
    exists(
        select 1
        from information_schema.statistics
        where table_schema = database()
          and table_name = 'growth_badge'
          and index_name = 'idx_growth_badge_event_threshold'
    ),
    'select 1',
    'create index idx_growth_badge_event_threshold on growth_badge(event_type, enabled_flag, deleted, threshold)'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
