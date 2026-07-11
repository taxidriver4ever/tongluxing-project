package com.tongluxing.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * App 使用小程序端一次性 ticket 绑定并登录。
 */
public record AppBindByMiniTicketRequest(
        @NotBlank(message = "绑定 ticket 不能为空")
        String ticket,

        @NotBlank(message = "设备标识不能为空")
        String deviceId,

        String deviceName,

        String platform
) {
}
