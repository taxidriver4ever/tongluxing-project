package com.tongdao.vehicle.mapper;

import java.time.LocalDateTime;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VehicleAuditLogMapper {

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
