package com.tongluxing.vehicle.entity;

import java.time.LocalDateTime;

import lombok.Data;

/** 车辆认证图片明细。 */
@Data
public class VehicleCertificationImage {
    private Long id;
    private Long certificationId;
    private Long vehicleId;
    private String imageType;
    private String imageKey;
    private Integer sortNo;
    private LocalDateTime createdAt;
    private Integer deleted;
}
