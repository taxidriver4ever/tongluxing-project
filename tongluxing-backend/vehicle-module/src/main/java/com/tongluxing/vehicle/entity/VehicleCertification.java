package com.tongluxing.vehicle.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 车辆认证记录实体，对应 vehicle_certification 表。
 *
 * <p>每次提交认证都会生成一条记录，车辆当前认证状态同步保存在 vehicle_profile 中。</p>
 */
@Data
public class VehicleCertification {
    /** 主键 ID。 */
    private Long id;
    /** 关联车辆 ID。 */
    private Long vehicleId;
    /** 提交认证的用户 ID。 */
    private Long userId;
    /** 行驶证所有人姓名。 */
    private String ownerName;
    /** 认证车牌号密文。 */
    private String plateNoCipher;
    /** 认证车牌号脱敏值。 */
    private String plateNoMask;
    /** 行驶证车辆类型。 */
    private String vehicleType;
    /** VIN 密文。 */
    private String vinCipher;
    /** VIN 脱敏值。 */
    private String vinMask;
    /** 发动机号密文及脱敏值。 */
    private String engineNoCipher;
    private String engineNoMask;
    /** 行驶证日期和发证机关。 */
    private LocalDate registerDate;
    private LocalDate issueDate;
    private String issuingAuthority;
    /** 行驶证正页/副页资源标识。 */
    private String licenseFrontImageKey;
    private String licenseBackImageKey;
    /** 小程序识别来源。 */
    private String recognitionSource;
    /** 审核状态。 */
    private String status;
    /** 审核拒绝原因。 */
    private String rejectReason;
    /** 提交时间。 */
    private LocalDateTime submittedAt;
    /** 审核时间。 */
    private LocalDateTime reviewedAt;
    /** 审核人 ID。 */
    private Long reviewerId;
}
