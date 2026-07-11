package com.tongluxing.vehicle.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 车辆认证记录响应。
 *
 * <p>只暴露脱敏后的车牌号、VIN 和发动机号，避免认证敏感资料直接出现在前端。</p>
 */
public record VehicleCertificationResponse(
        /** 关联车辆 ID。 */
        Long vehicleId,
        /** 行驶证所有人姓名。 */
        String ownerName,
        /** 脱敏后的车牌号。 */
        String plateNoMask,
        /** 脱敏后的 VIN。 */
        String vinMask,
        /** 脱敏后的发动机号。 */
        String engineNoMask,
        /** 行驶证正页/副页资源标识。 */
        String licenseFrontImageKey,
        String licenseBackImageKey,
        /** 车辆审核图片。 */
        List<VehicleCertificationImageVO> vehicleImages,
        /** 认证状态。 */
        String status,
        /** 审核拒绝原因；非拒绝状态通常为空。 */
        String rejectReason,
        /** 提交时间。 */
        LocalDateTime submittedAt,
        /** 审核时间。 */
        LocalDateTime reviewedAt,
        /** 当前状态是否允许重新提交。 */
        Boolean canResubmit
) {
}
