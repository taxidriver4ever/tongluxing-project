package com.tongluxing.user.vo;

import java.time.LocalDateTime;
import java.time.LocalDate;

/**
 * 后台驾驶证认证详情，图片 URL 由 admin-module 按需补充。
 *
 * <p>该模型包含完整姓名和驾驶证号，只能用于已经完成后台权限校验的审核链路，
 * 不得复用于普通用户接口或写入业务日志。</p>
 *
 * @param certificationId 认证申请主键
 * @param userId 申请用户 ID
 * @param holderName 解密后的持证人姓名
 * @param licenseNo 解密后的完整驾驶证号
 * @param vehicleClass 准驾车型
 * @param firstIssueDate 初次领证日期
 * @param validFrom 有效期开始日期
 * @param validTo 有效期截止日期
 * @param issuingAuthority 发证机关
 * @param licenseFrontImageKey 主页图片 Key
 * @param licenseBackImageKey 副页图片 Key
 * @param recognitionSource 识别来源
 * @param status 认证状态
 * @param rejectReason 驳回原因
 * @param submittedAt 提交时间
 * @param reviewedAt 审核完成时间
 */
public record DrivingLicenseAuditDetailVO(
        Long certificationId, Long userId, String holderName, String licenseNo, String vehicleClass,
        LocalDate firstIssueDate, LocalDate validFrom, LocalDate validTo, String issuingAuthority,
        String licenseFrontImageKey, String licenseBackImageKey, String recognitionSource,
        String status, String rejectReason, LocalDateTime submittedAt, LocalDateTime reviewedAt
) {
}

