-- 弱网 Pending Queue 主动压缩的服务端可观测字段。
-- 原始 record_time/sequence_no 仍保存在 driver_track_record，完整规划路线仍保存在 trip_route.polyline。
ALTER TABLE trip_track_summary
  ADD COLUMN track_quality VARCHAR(20) NOT NULL DEFAULT 'NORMAL' COMMENT '客户端轨迹完整度' AFTER hard_anomaly_count,
  ADD COLUMN raw_point_count INT NOT NULL DEFAULT 0 COMMENT '客户端原始采集点数' AFTER track_quality,
  ADD COLUMN uploaded_point_count INT NOT NULL DEFAULT 0 COMMENT '确认上传点数' AFTER raw_point_count,
  ADD COLUMN compressed_point_count INT NOT NULL DEFAULT 0 COMMENT '客户端主动压缩点数' AFTER uploaded_point_count,
  ADD COLUMN client_degraded_segment_count INT NOT NULL DEFAULT 0 COMMENT '客户端降级路段数' AFTER compressed_point_count;
