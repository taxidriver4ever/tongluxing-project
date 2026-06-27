package com.tongdao.vehicle.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 车辆档案实体，对应 vehicle_profile 表。
 *
 * <p>该实体保存用户车辆的基础展示信息、默认车辆标记和认证状态。
 * 车牌号使用密文和脱敏值分开存储，业务响应只暴露脱敏值。</p>
 */
@Data
public class VehicleProfile {
    /** 主键 ID。 */
    private Long id;
    /** 车辆所属用户 ID。 */
    private Long userId;
    /** 车牌号密文。 */
    private String plateNoCipher;
    /** 车牌号脱敏展示值。 */
    private String plateNoMask;
    /** 车辆品牌。 */
    private String brand;
    /** 车型名称。 */
    private String model;
    /** 车辆类型。 */
    private String vehicleType;
    /** 车辆颜色。 */
    private String color;
    /** 座位数。 */
    private Integer seatCount;
    /** 能源类型。 */
    private String energyType;
    /** 车辆照片资源标识。 */
    private String vehiclePhotoImageKey;
    /** 认证状态。 */
    private String certificationStatus;
    /** 默认车辆标记：1 默认，0 非默认。 */
    private Integer defaultFlag;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 更新时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记：0 未删除，1 已删除。 */
    private Integer deleted;
}
