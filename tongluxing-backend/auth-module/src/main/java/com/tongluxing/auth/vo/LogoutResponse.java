package com.tongluxing.auth.vo;

/**
 * 退出登录响应。
 *
 * @param success 当前 access token 是否已完成撤销；合法请求固定返回 true
 */
public record LogoutResponse(
        /** 是否退出成功。 */
        Boolean success
) {
}
