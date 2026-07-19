package com.tongluxing.user.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户模块查询结果 DTO。
 *
 * <p>用于承接用户资料、驾驶证认证和隐私设置相关 SQL 的查询结果。
 * 该对象只在数据访问和服务层之间流转，接口返回时会转换为对应 VO。</p>
 */
@Data
public class UserQueryDTO {
    /** 通用主键 ID，可能来自用户资料或驾驶证认证记录。 */
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 用户昵称。 */
    private String nickname;

    /** 头像图片资源标识。 */
    private String avatarImageKey;

    /** 性别：0 未知，1 男，2 女。 */
    private Integer gender;

    /** 生日。 */
    private LocalDate birthday;

    /** 城市编码。 */
    private String cityCode;

    /** 城市名称。 */
    private String cityName;

    /** 个人简介。 */
    private String bio;

    /** 用户资料状态，例如 ACTIVE。 */
    private String profileStatus;

    /** 驾驶证认证状态，例如 UNSUBMITTED、PENDING、APPROVED、REJECTED。 */
    private String certificationStatus;

    /** 驾驶证认证驳回原因。 */
    private String rejectReason;

    /** 认证提交时间。 */
    private LocalDateTime submittedAt;

    /** 认证审核时间。 */
    private LocalDateTime reviewedAt;

    /** 持证人姓名密文。 */
    private String holderNameCipher;

    /** 驾驶证号密文及脱敏值。 */
    private String licenseNoCipher;
    private String licenseNoMask;

    /** 准驾车型。 */
    private String vehicleClass;

    /** 驾驶证日期字段。 */
    private LocalDate firstIssueDate;
    private LocalDate validFrom;
    private LocalDate validTo;

    /** 发证机关。 */
    private String issuingAuthority;

    /** 驾驶证原图 Key。 */
    private String licenseFrontImageKey;
    private String licenseBackImageKey;

    /** 识别来源，当前固定为 MINIPROGRAM_OCR。 */
    private String recognitionSource;

    /** 后台审核人。 */
    private Long reviewerId;

    /** 个人主页可见性，例如 PUBLIC、PRIVATE。 */
    private String profileVisibility;

    /** 车辆信息可见性。 */
    private String vehicleVisibility;

    /** 是否允许邀请相关能力。 */
    private Boolean inviteEnabledFlag;

    private Integer totalTripCount;
    private Long totalDistanceMeters;
    private Long totalDurationMinutes;
    private Integer completedWaypointCount;
}
