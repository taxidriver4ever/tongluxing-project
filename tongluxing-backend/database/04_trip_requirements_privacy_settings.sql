-- 行程车型/预算与用户隐私设置增量迁移。
-- 适用于现有开发库；全新数据库已同步更新各模块 schema。

SET NAMES utf8mb4;

ALTER TABLE trip
    ADD COLUMN vehicle_requirements varchar(128) NOT NULL DEFAULT '不限' AFTER joined_vehicle_count,
    ADD COLUMN budget_description varchar(128) NULL AFTER vehicle_requirements;

ALTER TABLE trip_draft
    ADD COLUMN vehicle_requirements varchar(128) NOT NULL DEFAULT '不限' AFTER people_count,
    ADD COLUMN budget_description varchar(128) NULL AFTER vehicle_requirements,
    ADD COLUMN notes varchar(500) NULL AFTER budget_description;

ALTER TABLE user_privacy_setting
    ADD COLUMN city_visible_flag tinyint NOT NULL DEFAULT 1 AFTER invite_enabled_flag,
    ADD COLUMN bio_visible_flag tinyint NOT NULL DEFAULT 1 AFTER city_visible_flag,
    ADD COLUMN trip_stats_visible_flag tinyint NOT NULL DEFAULT 1 AFTER bio_visible_flag,
    ADD COLUMN level_visible_flag tinyint NOT NULL DEFAULT 1 AFTER trip_stats_visible_flag,
    ADD COLUMN location_enabled_flag tinyint NOT NULL DEFAULT 1 AFTER level_visible_flag,
    ADD COLUMN notification_enabled_flag tinyint NOT NULL DEFAULT 1 AFTER location_enabled_flag;

-- 推荐行程补充真实车型要求和预算，避免详情继续展示占位文案。
UPDATE trip
SET vehicle_requirements = CASE MOD(id, 4)
        WHEN 0 THEN 'SUV,越野车'
        WHEN 1 THEN '轿车,SUV'
        WHEN 2 THEN '摩托车'
        ELSE '不限'
    END,
    budget_description = CASE MOD(id, 3)
        WHEN 0 THEN '约1500元/人，油费路费AA'
        WHEN 1 THEN '约800元/人，餐饮住宿自理'
        ELSE NULL
    END
WHERE deleted = 0;
