package com.tongluxing.auth.vo;

/**
 * 小程序端生成的 App 绑定 ticket。
 */
public record AppBindTicketResponse(
        String ticket,
        Integer expireSeconds
) {
}
