package com.tongluxing.auth.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.auth.dto.AdminLoginRequest;
import com.tongluxing.auth.service.AdminAuthService;
import com.tongluxing.auth.vo.AdminCurrentOperatorResponse;
import com.tongluxing.auth.vo.AdminLoginResponse;
import com.tongluxing.auth.vo.AdminLogoutResponse;
import com.tongluxing.common.result.Result;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Web Admin 独立登录、会话检查与退出接口。
 *
 * <p>路由固定在 {@code /v1/admin/auth}。只有登录接口在安全白名单中；
 * {@code /me} 与 {@code /logout} 必须携带 Admin 不透明 Bearer Token。
 * 控制器不接触固定密码或 Redis，只负责输入校验和统一 {@code Result} 包装。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/auth")
public class AdminAuthController {
    /** 独立 Admin 认证服务，不复用普通用户 JWT 登录服务。 */
    private final AdminAuthService adminAuthService;

    /**
     * 校验后台凭据并签发新的 Admin 会话。
     *
     * @param request 登录名和密码
     * @return 操作员信息及不透明会话 Token
     */
    @PostMapping("/login")
    public Result<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        // 固定凭据比较、失败限流和不透明会话签发都由独立 Admin 服务完成。
        return Result.success(adminAuthService.login(request));
    }

    /**
     * 返回当前 Admin 会话中的操作员信息。
     *
     * @param authorization 完整 Bearer 请求头
     * @return 当前操作员与会话绝对过期时间
     */
    @GetMapping("/me")
    public Result<AdminCurrentOperatorResponse> current(
            @RequestHeader("Authorization") String authorization) {
        // 重新校验 Redis current 索引，保证被后一次登录顶下线的旧页面立即失效。
        return Result.success(adminAuthService.current(authorization));
    }

    /**
     * 撤销当前 Admin 会话并清理当前会话索引。
     *
     * @param authorization 完整 Bearer 请求头
     * @return 退出成功标识
     */
    @PostMapping("/logout")
    public Result<AdminLogoutResponse> logout(
            @RequestHeader("Authorization") String authorization) {
        // Service 校验会话后执行比较删除，避免旧退出请求误删新会话。
        return Result.success(adminAuthService.logout(authorization));
    }
}
