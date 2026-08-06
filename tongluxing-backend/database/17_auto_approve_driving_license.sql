-- 驾驶证材料齐全自动通过升级脚本。
-- 后端使用 spring.sql.init.mode=always 时启动会自动执行同等迁移；
-- 仅在需要手工升级已有数据库时单独执行本文件。

USE tongluxing;
SET NAMES utf8mb4;
START TRANSACTION;

UPDATE user_driving_license_certification
SET certification_status='APPROVED',
    reject_reason=NULL,
    reviewer_id=NULL,
    reviewed_at=COALESCE(reviewed_at, NOW()),
    updated_at=NOW()
WHERE certification_status='PENDING'
  AND deleted=0
  AND holder_name_cipher IS NOT NULL AND holder_name_cipher<>''
  AND license_no_cipher IS NOT NULL AND license_no_cipher<>''
  AND vehicle_class IS NOT NULL AND vehicle_class<>''
  AND license_front_image_key IS NOT NULL AND license_front_image_key<>''
  AND license_back_image_key IS NOT NULL AND license_back_image_key<>'';

COMMIT;
