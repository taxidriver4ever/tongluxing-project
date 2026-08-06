package com.tongluxing.user.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 驾驶证认证提交请求，字段由小程序 OCR 后经用户确认。
 *
 * <p>姓名和完整证件号进入 Service 后会先加密再落库；图片字段只传对象存储 Key，
 * 不接受客户端拼装的临时访问 URL。</p>
 *
 * @param holderName 持证人姓名
 * @param licenseNo 完整驾驶证号
 * @param vehicleClass 准驾车型
 * @param firstIssueDate 初次领证日期
 * @param validFrom 当前有效期开始日期
 * @param validTo 当前有效期截止日期
 * @param issuingAuthority 发证机关
 * @param licenseFrontImageKey 驾驶证主页图片 Key
 * @param licenseBackImageKey 驾驶证副页图片 Key
 * @param recognitionSource MINIPROGRAM_OCR 或 MANUAL_UPLOAD
 */
public record CertificationRequest(
        @NotBlank @Size(max = 64) String holderName,
        @NotBlank @Pattern(regexp = "^[0-9A-Za-z]{6,32}$") String licenseNo,
        @NotBlank @Size(max = 32) String vehicleClass,
        LocalDate firstIssueDate,
        LocalDate validFrom,
        LocalDate validTo,
        @Size(max = 128) String issuingAuthority,
        @NotBlank @Size(max = 512) String licenseFrontImageKey,
        @NotBlank @Size(max = 512) String licenseBackImageKey,
        @NotBlank @Pattern(regexp = "MINIPROGRAM_OCR|MANUAL_UPLOAD") String recognitionSource
) {
}
