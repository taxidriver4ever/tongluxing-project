package com.tongluxing.team.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 入队或归队申请。
 *
 * <p>乘客可以关联队内车辆/车主，也可以输入车牌作为线索；完整车牌不会直接存库，
 * 服务层会转换为脱敏值。归队申请必须携带当前位置。</p>
 */
public record JoinTeamApplicationRequest(
        Long applicantVehicleId,
        @Size(max = 255) String applyMessage,
        String joinQuestionJson,
        @Pattern(regexp = "JOIN|RETURN") String applicationType,
        @Pattern(regexp = "DRIVER|PASSENGER") String joinRole,
        Long linkedOwnerUserId,
        Long linkedVehicleId,
        @Size(max = 24) String plateNumber,
        BigDecimal currentLatitude,
        BigDecimal currentLongitude
) {
    /** 兼容旧客户端仅提交车辆、备注和问卷。 */
    public JoinTeamApplicationRequest(Long applicantVehicleId, String applyMessage, String joinQuestionJson) {
        this(applicantVehicleId, applyMessage, joinQuestionJson, "JOIN", null,
                null, null, null, null, null);
    }
}
