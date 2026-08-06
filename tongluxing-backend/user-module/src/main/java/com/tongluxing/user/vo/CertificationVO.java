package com.tongluxing.user.vo;

import java.time.LocalDateTime;

/**
 * 驾驶证认证状态返回对象。
 *
 * @param certificationId 认证申请主键；UNSUBMITTED 时为空
 * @param userId 申请所属用户 ID
 * @param status UNSUBMITTED、PENDING、APPROVED 或 REJECTED
 * @param rejectReason 驳回原因
 * @param submittedAt 提交时间
 * @param reviewedAt 系统自动通过或异常复核完成时间
 * @param canResubmit 当前状态是否允许再次提交
 */
public record CertificationVO(
        Long certificationId, Long userId, String status, String rejectReason,
        LocalDateTime submittedAt, LocalDateTime reviewedAt, Boolean canResubmit
) {
}

