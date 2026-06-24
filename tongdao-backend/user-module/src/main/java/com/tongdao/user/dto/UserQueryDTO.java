package com.tongdao.user.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 用户模块查询结果 DTO。
 *
 * <p>用于承接用户资料、实名认证和隐私设置相关 SQL 的查询结果。
 * 该对象只在数据访问和服务层之间流转，接口返回时会转换为对应 VO。</p>
 */
@Data
public class UserQueryDTO {
    /** 通用主键 ID，可能来自用户资料或实名认证记录。 */
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

    /** 实名认证状态，例如 UNSUBMITTED、PENDING、APPROVED、REJECTED。 */
    private String certificationStatus;

    /** 实名认证驳回原因。 */
    private String rejectReason;

    /** 认证提交时间。 */
    private LocalDateTime submittedAt;

    /** 认证审核时间。 */
    private LocalDateTime reviewedAt;

    /** 个人主页可见性，例如 PUBLIC、PRIVATE。 */
    private String profileVisibility;

    /** 车辆信息可见性。 */
    private String vehicleVisibility;

    /** 是否允许邀请相关能力。 */
    private Boolean inviteEnabledFlag;
}
