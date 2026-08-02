package com.tongluxing.storage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 获取上传预签名 URL 请求。
 */
public record PresignUploadRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 128) String contentType,
        @NotNull @Positive Long fileSize,
        @NotBlank @Size(max = 64)
        @Pattern(regexp = "USER_AVATAR|USER_DRIVER_LICENSE_FRONT|USER_DRIVER_LICENSE_BACK|"
                + "VEHICLE_LICENSE_FRONT|VEHICLE_LICENSE_BACK|VEHICLE_PHOTO_FRONT|VEHICLE_PHOTO_REAR|"
                + "VEHICLE_PHOTO_SIDE|VEHICLE_PHOTO_OTHER|MERCHANT_COVER|MERCHANT_LICENSE|"
                + "MERCHANT_QUALIFICATION|MERCHANT_PRODUCT_IMAGE|MERCHANT_QR|TRIP_COVER|TRIP_ROUTE_FILE|CHAT_IMAGE|CHAT_FILE",
                message = "不支持的文件业务类型")
        String bizType,
        @Size(max = 128) String bizId
) {
}
