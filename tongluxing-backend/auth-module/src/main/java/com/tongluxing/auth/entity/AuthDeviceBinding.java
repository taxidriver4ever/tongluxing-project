package com.tongluxing.auth.entity;

import java.time.LocalDateTime;

import lombok.Data;

/**
 * 认证账号与客户端设备的绑定记录，对应 {@code auth_device_binding} 表。
 *
 * <p>该表用于设备审计和展示最近登录终端，不承担当前会话有效性判断；
 * 会话是否有效仍以 JWT 声明和 Redis 中的 JTI 索引为准。同一用户、客户端类型、
 * 设备标识的组合由服务层执行新增或更新。</p>
 */
@Data
public class AuthDeviceBinding {
    /** 设备绑定记录主键，使用雪花算法生成。 */
    private Long id;
    /** 全局业务用户 ID，对应 {@code auth_account.user_id}。 */
    private Long userId;
    /** 登录手机号快照，便于审计；账号手机号变化时登录更新会同步刷新。 */
    private String phone;
    /** 客户端类型，例如 MINI_PROGRAM、APP_DRIVER 或 MERCHANT_WEB。 */
    private String clientType;
    /** 客户端上报的稳定设备标识；为空的入口会被服务层规范化为 default。 */
    private String deviceId;
    /** 可读设备名称，例如手机品牌与型号。 */
    private String deviceName;
    /** 操作系统或客户端平台，例如 Android、iOS、Web。 */
    private String platform;
    /** 绑定状态：1 表示当前有效；预留其他值用于解绑或风控冻结。 */
    private Integer bindStatus;
    /** 该设备最近一次成功登录时间。 */
    private LocalDateTime lastLoginTime;
    /** 该设备最近一次成功登录 IP。 */
    private String lastLoginIp;
    /** 首次建立绑定关系的时间。 */
    private LocalDateTime createdAt;
    /** 最近一次更新设备信息或登录信息的时间。 */
    private LocalDateTime updatedAt;
    /** 逻辑删除标记：0 未删除，1 已删除。 */
    private Integer deleted;
}
