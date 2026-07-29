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
    /** 当前 SQL 主记录的主键 ID，可能对应用户资料或驾驶证认证申请。 */
    private Long id;

    /** 平台用户 ID，是用户域数据与认证账号、行程等模块建立关联的业务键。 */
    private Long userId;

    /** 面向用户展示、全局唯一且不可变的同路行号。 */
    private String tongluxingId;

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

    /** 驾驶证认证驳回原因；仅 REJECTED 状态有业务含义。 */
    private String rejectReason;

    /** 认证提交时间。 */
    private LocalDateTime submittedAt;

    /** 认证审核时间。 */
    private LocalDateTime reviewedAt;

    /** 持证人姓名的 AES-GCM 密文，只允许 Service 在授权审核场景解密。 */
    private String holderNameCipher;

    /** 驾驶证号的 AES-GCM 密文，不可直接写入接口响应或日志。 */
    private String licenseNoCipher;

    /** 驾驶证号脱敏展示值，供无需查看完整证件号的后台列表使用。 */
    private String licenseNoMask;

    /** 准驾车型。 */
    private String vehicleClass;

    /** 驾驶证初次领证日期。 */
    private LocalDate firstIssueDate;

    /** 驾驶证当前有效期起始日期。 */
    private LocalDate validFrom;

    /** 驾驶证当前有效期截止日期。 */
    private LocalDate validTo;

    /** 发证机关。 */
    private String issuingAuthority;

    /** 驾驶证主页图片的对象存储 Key，不是可直接公开访问的 URL。 */
    private String licenseFrontImageKey;

    /** 驾驶证副页图片的对象存储 Key；手工上传时可以为空。 */
    private String licenseBackImageKey;

    /** 资料识别来源，例如 MINIPROGRAM_OCR 或 MANUAL_UPLOAD。 */
    private String recognitionSource;

    /** 执行人工审核的后台操作员 ID；待审核记录为空。 */
    private Long reviewerId;

    /** 个人主页可见性，例如 PUBLIC、PRIVATE。 */
    private String profileVisibility;

    /** 车辆信息可见性。 */
    private String vehicleVisibility;

    /** 是否允许邀请码及邀请关系相关能力。 */
    private Boolean inviteEnabledFlag;

    /** 公开主页是否展示所在城市。 */
    private Boolean cityVisibleFlag;

    /** 公开主页是否展示个人简介。 */
    private Boolean bioVisibleFlag;

    /** 公开主页是否展示行程次数、距离、时长和途经点等统计。 */
    private Boolean tripStatsVisibleFlag;

    /** 用户等级是否允许被其他模块或公开页面展示。 */
    private Boolean levelVisibleFlag;

    /** 用户是否授权需要定位信息的功能。 */
    private Boolean locationEnabledFlag;

    /** 用户是否允许接收业务通知。 */
    private Boolean notificationEnabledFlag;

    /** 当前资料或隐私设置最后更新时间，用于数据库更新与审计。 */
    private LocalDateTime updatedAt;

    /** 已累计完成或发布的行程数量；缺少统计记录时按 0 处理。 */
    private Integer totalTripCount;

    /** 累计行驶距离，单位为米。 */
    private Long totalDistanceMeters;

    /** 累计行程时长，单位为分钟。 */
    private Long totalDurationMinutes;

    /** 累计完成的途经点数量。 */
    private Integer completedWaypointCount;
}
