package com.tongdao.auth.vo;

/**
 * 退出登录响应。
 */
public record LogoutResponse(
        /** 是否退出成功。 */
        Boolean success
) {
}
