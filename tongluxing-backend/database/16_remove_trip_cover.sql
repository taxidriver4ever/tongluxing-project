-- 移除行程/车队封面功能的增量迁移。
-- 已有数据库执行本文件；空数据库直接执行 01_reset_and_create_all_tables.sql。
-- 商家入驻与优惠券封面字段不受影响。

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'trip'
     AND column_name = 'cover_image_key') > 0,
  'ALTER TABLE trip DROP COLUMN cover_image_key',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE() AND table_name = 'trip_draft'
     AND column_name = 'cover_image_key') > 0,
  'ALTER TABLE trip_draft DROP COLUMN cover_image_key',
  'SELECT 1'
);
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
