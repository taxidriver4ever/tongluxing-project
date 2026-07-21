-- MVP 勋章规则补充：明确条件文案并修正里程、邀请事件口径。
SET @growth_add_badge_description = IF(
  (SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE()
    AND table_name='growth_badge' AND column_name='condition_description')=0,
  'ALTER TABLE growth_badge ADD COLUMN condition_description VARCHAR(128) NULL AFTER badge_image_key',
  'SELECT 1');
PREPARE growth_stmt FROM @growth_add_badge_description;
EXECUTE growth_stmt;
DEALLOCATE PREPARE growth_stmt;

UPDATE growth_badge SET condition_description = '完成首次组队行程', event_type = 'TEAM_TRIP_COMPLETED', threshold = 1 WHERE badge_code = 'FIRST_DEPARTURE' AND deleted = 0;
UPDATE growth_badge SET condition_description = '累计有效行驶 100 公里', event_type = 'TRIP_MILEAGE', threshold = 10 WHERE badge_code = 'DISTANCE_100KM' AND deleted = 0;
UPDATE growth_badge SET condition_description = '累计有效行驶 1000 公里', event_type = 'TRIP_MILEAGE', threshold = 100 WHERE badge_code = 'DISTANCE_1000KM' AND deleted = 0;
UPDATE growth_badge SET condition_description = '累计有效行驶 10000 公里', event_type = 'TRIP_MILEAGE', threshold = 1000 WHERE badge_code = 'DISTANCE_10000KM' AND deleted = 0;
UPDATE growth_badge SET condition_description = '成功邀请 10 位新用户注册', event_type = 'INVITE_USER_REGISTER', threshold = 10 WHERE badge_code = 'INVITE_10' AND deleted = 0;
UPDATE growth_badge SET condition_description = '成功邀请 50 位新用户注册', event_type = 'INVITE_USER_REGISTER', threshold = 50 WHERE badge_code = 'INVITE_50' AND deleted = 0;
UPDATE growth_badge SET condition_description = '完成 10 次互助' WHERE badge_code = 'HELP_10' AND deleted = 0;
UPDATE growth_badge SET condition_description = '完成 50 次互助' WHERE badge_code = 'HELP_50' AND deleted = 0;
UPDATE growth_badge SET condition_description = '完成 G318 川藏线路线' WHERE badge_code = 'ROUTE_G318' AND deleted = 0;
UPDATE growth_badge SET condition_description = '春夏秋冬各完成一次组队' WHERE badge_code = 'FOUR_SEASONS' AND deleted = 0;

ALTER TABLE growth_badge
  MODIFY COLUMN condition_description VARCHAR(128) NOT NULL;
