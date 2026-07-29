package com.tongluxing.auth.vo;

/**
 * 当前 Web Admin 操作员信息。
 *
 * @param operatorId 后台操作员业务 ID，用于审计记录，不是普通用户 ID
 * @param username 后台登录名
 * @param displayName 后台界面展示名称
 * @param expireAt 会话绝对过期时间，Unix 秒；前端可据此主动跳转登录页
 */
public record AdminCurrentOperatorResponse(
        Long operatorId,
        String username,
        String displayName,
        Long expireAt
) {
}
