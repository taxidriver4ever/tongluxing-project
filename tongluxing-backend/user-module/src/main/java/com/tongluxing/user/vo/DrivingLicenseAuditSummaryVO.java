package com.tongluxing.user.vo;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * 后台驾驶证认证列表项。
 *
 * <p>列表只返回脱敏证件号；持证人姓名虽需解密展示，但完整证件号只在进入审核
 * 详情时按需解密，以缩小敏感数据暴露范围。</p>
 *
 * @param certificationId 认证申请主键
 * @param userId 申请用户 ID
 * @param holderName 解密后的持证人姓名
 * @param licenseNoMasked 脱敏驾驶证号
 * @param vehicleClass 准驾车型
 * @param validTo 有效期截止日期
 * @param status 当前认证状态
 * @param submittedAt 提交时间
 */
public record DrivingLicenseAuditSummaryVO(
        Long certificationId, Long userId, String holderName, String licenseNoMasked,
        String vehicleClass, LocalDate validTo, String status, LocalDateTime submittedAt
) {
}

