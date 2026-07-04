package com.tongluxing.vehicle.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 车辆操作审计日志 Mapper。
 *
 * <p>记录车辆创建、更新、删除、设置默认和提交认证时的关键快照，便于追溯数据变化。</p>
 */
@Mapper
public interface VehicleAuditLogMapper {

    /** 写入车辆操作审计日志。 */
    @Insert("""
            insert into vehicle_audit_log
                (id, vehicle_id, user_id, operation_type, before_snapshot, after_snapshot, remark, created_at)
            values
                (#{id}, #{vehicleId}, #{userId}, #{operationType}, #{beforeSnapshot}, #{afterSnapshot}, #{remark}, #{createdAt})
            """)
    void insert(
            @Param("id") Long id,
            @Param("vehicleId") Long vehicleId,
            @Param("userId") Long userId,
            @Param("operationType") String operationType,
            @Param("beforeSnapshot") String beforeSnapshot,
            @Param("afterSnapshot") String afterSnapshot,
            @Param("remark") String remark,
            @Param("createdAt") LocalDateTime createdAt
    );
}
