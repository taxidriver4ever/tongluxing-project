package com.tongluxing.notify.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** App 向后端登记系统推送 Token。 */
public record RegisterPushDeviceRequest(
        @NotBlank @Size(max = 128) String deviceId,
        @NotBlank @Pattern(regexp = "ANDROID|IOS") String platform,
        @NotBlank @Size(max = 32) String vendor,
        @NotBlank @Size(max = 512) String pushToken,
        @Size(max = 32) String appVersion
) {
}
