package com.tongluxing.auth.vo;

/**
 * Web Admin 退出登录响应。
 *
 * @param success 是否完成退出；当前合法会话删除成功后固定返回 true
 */
public record AdminLogoutResponse(Boolean success) {
}
