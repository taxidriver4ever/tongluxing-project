-- 仅用于专项压测；独立 ID 段 930000000000000000~930000000000009999。
-- 可重复执行，只 INSERT/ON DUPLICATE KEY UPDATE 本脚本自己的 PERF 数据。
SET NAMES utf8mb4;
START TRANSACTION;

-- 用户 13910000001 的压测基准行程，同时供 RUNNING 轨迹上传使用。
INSERT INTO trip (
  id, trip_number, user_id, captain_user_id, title, description, expected_people,
  start_name, start_lat, start_lng, start_location_name, start_location_address, start_latitude, start_longitude,
  end_name, end_lat, end_lng, end_location_name, end_location_address, end_latitude, end_longitude,
  route_summary, route_distance, route_duration, departure_time, estimated_days, total_distance_meters,
  max_vehicle_count, joined_vehicle_count, travel_depth, public_flag, status, remark, created_at, updated_at, deleted
) VALUES (
  930000000000000000, 'PERF0000000000000000', 910000000000000101, 910000000000000101,
  'PERF 基准与轨迹行程', '专项压测基准数据', 4,
  '广州天河',23.134700,113.361200,'广州天河','PERF',23.134700,113.361200,
  '深圳湾',22.486900,113.945600,'深圳湾','PERF',22.486900,113.945600,
  'PERF 广州至深圳',145000,9000,DATE_ADD(NOW(),INTERVAL 2 DAY),2,145000,
  4,1,'LIGHT',1,'RUNNING','PERFORMANCE_TEST',NOW(),NOW(),0
) ON DUPLICATE KEY UPDATE departure_time=VALUES(departure_time),status='RUNNING',updated_at=NOW(),deleted=0;

-- 0..999 数字集。CANDIDATE_COUNT 通过把较大 ID 的数据临时标记 deleted=1 来分档，不做删除。
INSERT INTO trip (
  id,trip_number,user_id,title,description,expected_people,
  start_name,start_lat,start_lng,start_location_name,start_location_address,start_latitude,start_longitude,
  end_name,end_lat,end_lng,end_location_name,end_location_address,end_latitude,end_longitude,
  route_summary,route_distance,route_duration,departure_time,estimated_days,total_distance_meters,
  max_vehicle_count,joined_vehicle_count,travel_depth,public_flag,status,remark,created_at,updated_at,deleted
)
SELECT 930000000000001000+n, CONCAT('PC',LPAD(n,18,'0')), 910000000000000102+(n%49),
  CONCAT('PERF 候选路线 ',LPAD(n,4,'0')),'专项压测候选数据',4,
  '广州天河',23.134700+(n%10)*0.0001,113.361200,'广州天河','PERF',23.134700+(n%10)*0.0001,113.361200,
  '深圳湾',22.486900,113.945600+(n%10)*0.0001,'深圳湾','PERF',22.486900,113.945600+(n%10)*0.0001,
  'PERF 广州至深圳',145000+(n%20)*100,9000,DATE_ADD(NOW(),INTERVAL 2 DAY),2,145000,
  6,1,'LIGHT',1,'PUBLISHED','PERFORMANCE_TEST',NOW(),NOW(),0
FROM (
  SELECT u.d+10*t.d+100*h.d AS n
  FROM (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) u
  CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t
  CROSS JOIN (SELECT 0 d UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) h
) nums
ON DUPLICATE KEY UPDATE departure_time=VALUES(departure_time),status='PUBLISHED',updated_at=NOW(),deleted=0;

INSERT INTO trip_route (id,trip_id,draft_id,route_plan_id,origin,destination,waypoints,polyline,match_polyline,plan_distance,plan_duration,provider_type,route_status,created_at,updated_at,deleted)
SELECT 930000000000003000+(t.id-930000000000001000),t.id,NULL,NULL,
 JSON_OBJECT('name','广州天河','latitude',t.start_latitude,'longitude',t.start_longitude),
 JSON_OBJECT('name','深圳湾','latitude',t.end_latitude,'longitude',t.end_longitude),NULL,
 JSON_ARRAY(
   JSON_OBJECT('name','起点','latitude',23.134700,'longitude',113.361200),
   JSON_OBJECT('name','转折1','latitude',23.020000,'longitude',113.520000),
   JSON_OBJECT('name','转折2','latitude',22.800000,'longitude',113.700000),
   JSON_OBJECT('name','终点','latitude',22.486900,'longitude',113.945600)),
 JSON_ARRAY(
   JSON_OBJECT('name','起点','latitude',23.134700,'longitude',113.361200),
   JSON_OBJECT('name','转折1','latitude',23.020000,'longitude',113.520000),
   JSON_OBJECT('name','转折2','latitude',22.800000,'longitude',113.700000),
   JSON_OBJECT('name','终点','latitude',22.486900,'longitude',113.945600)),
 t.route_distance,t.route_duration,'PERFORMANCE_TEST','VALID',NOW(),NOW(),0
FROM trip t WHERE t.id BETWEEN 930000000000001000 AND 930000000000001999
ON DUPLICATE KEY UPDATE polyline=VALUES(polyline),match_polyline=VALUES(match_polyline),updated_at=NOW(),deleted=0;

INSERT INTO trip_route (id,trip_id,draft_id,route_plan_id,origin,destination,waypoints,polyline,match_polyline,plan_distance,plan_duration,provider_type,route_status,created_at,updated_at,deleted)
SELECT 930000000000002000,930000000000000000,NULL,NULL,
 JSON_OBJECT('name','广州天河','latitude',23.134700,'longitude',113.361200),
 JSON_OBJECT('name','深圳湾','latitude',22.486900,'longitude',113.945600),NULL,
 JSON_ARRAY(JSON_OBJECT('name','起点','latitude',23.134700,'longitude',113.361200),JSON_OBJECT('name','终点','latitude',22.486900,'longitude',113.945600)),
 JSON_ARRAY(JSON_OBJECT('name','起点','latitude',23.134700,'longitude',113.361200),JSON_OBJECT('name','终点','latitude',22.486900,'longitude',113.945600)),
 145000,9000,'PERFORMANCE_TEST','VALID',NOW(),NOW(),0
ON DUPLICATE KEY UPDATE updated_at=NOW(),deleted=0;

INSERT INTO team (id,trip_id,owner_user_id,owner_vehicle_id,team_name,team_desc,start_name,end_name,departure_time,max_member_count,current_member_count,join_mode,team_status,public_flag,notice,created_at,updated_at,deleted)
SELECT 930000000000005000+(t.id-930000000000001000),t.id,t.user_id,0,CONCAT('PERF车队',t.id),'专项压测','广州天河','深圳湾',t.departure_time,6,1,'APPLICATION','ACTIVE',1,'PERFORMANCE_TEST',NOW(),NOW(),0
FROM trip t WHERE t.id BETWEEN 930000000000001000 AND 930000000000001999
ON DUPLICATE KEY UPDATE departure_time=VALUES(departure_time),team_status='ACTIVE',public_flag=1,updated_at=NOW(),deleted=0;

COMMIT;
SELECT COUNT(*) AS perf_candidate_count FROM trip WHERE id BETWEEN 930000000000001000 AND 930000000000001999 AND deleted=0;
