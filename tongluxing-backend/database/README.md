# 同路行数据库重建与测试账号

## 文件

- `01_reset_and_create_all_tables.sql`：删除并重建当前数据库中的 103 张同路行业务表，同时写入成长等级、勋章和地图地点目录等基础数据。
- `02_create_test_users.sql`：创建 8 个联调用户、公开资料、统计数据、成长账户、邀请码、关注关系、已认证车辆和驾驶证测试记录。
- `03_mock_recommended_trips.sql`：为推荐行程页创建 6 条带途经点的公开招募行程，并为 13888888888 的最新活跃行程生成推荐结果。

## 执行顺序

1. 停止后端服务，避免重建过程中仍有请求写入数据库。
2. 备份当前开发数据库。
3. 确认连接的是开发或测试数据库，执行：

```sql
SELECT DATABASE();
```

4. 依次执行：

```text
01_reset_and_create_all_tables.sql
02_create_test_users.sql
03_mock_recommended_trips.sql
```

5. 覆盖补丁中的后端文件并重新启动 Spring Boot。

> `01_reset_and_create_all_tables.sql` 会永久删除当前数据库中的全部同路行业务数据，不得在生产库执行。

## 测试账号

所有账号统一密码：`12345678`

| 手机号 | 昵称 | 同路行号 | 默认车辆 ID |
|---|---|---|---:|
| 13888888881 | 测试队长阿航 | TLX000101 | 900000000000008001 |
| 13888888882 | 广州车友小陈 | TLX000102 | 900000000000008002 |
| 13888888883 | 深圳车友小林 | TLX000103 | 900000000000008003 |
| 13888888884 | 成都车友老周 | TLX000104 | 900000000000008004 |
| 13888888885 | 摄影搭子小雨 | TLX000105 | 900000000000008005 |
| 13888888886 | 露营玩家阿森 | TLX000106 | 900000000000008006 |
| 13888888887 | 新手司机小唐 | TLX000107 | 900000000000008007 |
| 13888888888 | 旅行达人安安 | TLX000108 | 900000000000008008 |

测试证件、车牌和图片路径均为虚构数据，仅用于开发联调。测试图片对象未实际上传到 MinIO，因此证件图片可能显示为空或加载失败，但不影响登录、关注和行程业务测试。

## 重建后检查

```sql
SHOW COLUMNS FROM user_profile LIKE 'tongluxing_id';

SELECT COUNT(*) AS table_count
FROM information_schema.tables
WHERE table_schema = DATABASE();

SELECT a.phone, p.nickname, p.tongluxing_id, a.user_id
FROM auth_account a
JOIN user_profile p ON p.user_id = a.user_id AND p.deleted = 0
WHERE a.phone BETWEEN '13888888881' AND '13888888888'
ORDER BY a.phone;
```

- `14_trip_search_history.sql`：为已有数据库新增行程搜索历史表，支持新行程搜索页的跨设备历史记录。
- `15_trip_real_heat_and_search_match.sql`：修复搜索页基准行程顺路率，并将推荐热度切换为真实报名、收藏、评价数据；同时补齐聚合索引。
- `16_remove_trip_cover.sql`：从已有数据库的 `trip` 与 `trip_draft` 表移除行程/车队封面字段；商家和优惠券封面不受影响。
