package com.tongluxing.auth.service;

import com.tongluxing.auth.dto.AdminLoginRequest;
import com.tongluxing.auth.vo.AdminCurrentOperatorResponse;
import com.tongluxing.auth.vo.AdminLoginResponse;
import com.tongluxing.auth.vo.AdminLogoutResponse;

/**
 * Web Admin 独立认证用例。
 *
 * <p>Admin 使用配置凭据和 Redis 不透明会话，与普通用户的手机号账号、JWT、
 * refresh token 和动态角色完全隔离。Controller 只转发请求头与参数，安全校验均在实现层完成。</p>
 */
public interface AdminAuthService {
    /**
     * 校验固定后台凭据并建立新的单点会话。
     *
     * @param request 登录名和密码
     * @return 仅本次响应可见的明文会话 Token 与操作员信息
     */
    AdminLoginResponse login(AdminLoginRequest request);

    /**
     * 解析 Bearer Token 并返回当前后台操作员。
     *
     * @param authorization 完整 Authorization 请求头
     * @return 当前有效 Admin 会话中的操作员信息
     */
    AdminCurrentOperatorResponse current(String authorization);

    /**
     * 撤销当前 Admin 会话。
     *
     * @param authorization 完整 Authorization 请求头
     * @return 退出结果
     */
    AdminLogoutResponse logout(String authorization);
}
