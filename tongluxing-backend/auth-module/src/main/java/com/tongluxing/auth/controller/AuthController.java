package com.tongluxing.auth.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.tongluxing.auth.dto.LoginRequest;
import com.tongluxing.auth.dto.AppLoginRequest;
import com.tongluxing.auth.dto.PasswordLoginRequest;
import com.tongluxing.auth.dto.RefreshTokenRequest;
import com.tongluxing.auth.dto.SmsCodeRequest;
import com.tongluxing.auth.dto.SetPasswordRequest;
import com.tongluxing.auth.dto.WxPhoneLoginRequest;
import com.tongluxing.auth.service.AuthService;
import com.tongluxing.auth.vo.CurrentUserResponse;
import com.tongluxing.auth.vo.LoginResponse;
import com.tongluxing.auth.vo.LogoutResponse;
import com.tongluxing.auth.vo.RefreshTokenResponse;
import com.tongluxing.auth.vo.SmsCodeResponse;
import com.tongluxing.common.result.Result;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 认证模块对外接口。
 *
 * <p>负责短信验证码、手机号登录、微信手机号登录、退出登录、当前登录用户查询和令牌刷新。</p>
 *
 * <p>方法只承担 HTTP 参数绑定、Bean Validation 和统一 {@code Result} 封装。
 * Token 解析、账号创建、密码比较、设备绑定、单点登录与日志记录全部委托给 AuthService，
 * 从而让不同入口共享同一安全规则。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/auth")
public class AuthController {

    /** 认证业务服务，封装登录校验、令牌签发、日志记录等核心流程。 */
    private final AuthService authService;

    /**
     * 发送短信验证码，用于手机号验证码登录。
     *
     * @param request 手机号与验证码场景
     * @return 验证码有效期，不包含验证码正文
     */
    @PostMapping("/sms-code")
    public Result<SmsCodeResponse> sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        // 请求体格式先由 @Valid 校验，冷却、每日上限和 Redis 写入交给认证服务。
        return Result.success(authService.sendSmsCode(request));
    }

    /**
     * 手机号 + 短信验证码登录；新用户会自动创建认证账号。
     *
     * @return access/refresh 令牌及首次登录状态
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        // Controller 不判断新老用户；Service 统一完成验证码消费、账号创建和令牌签发。
        return Result.success(authService.login(request));
    }

    /**
     * 手机号 + 密码登录；不自动注册。
     *
     * @return 新令牌对；登录后旧设备会话失效
     */
    @PostMapping("/password-login")
    public Result<LoginResponse> passwordLogin(@Valid @RequestBody PasswordLoginRequest request) {
        // 明文密码只向下传递给 PasswordEncoder 校验，控制器不记录或加工密码。
        return Result.success(authService.passwordLogin(request));
    }

    /**
     * 微信小程序手机号授权登录；服务端通过微信 code 换取手机号。
     *
     * @param request 微信 code 或联调 mockPhone，以及设备与注册来源
     */
    @PostMapping("/wx-phone-login")
    public Result<LoginResponse> wxPhoneLogin(@Valid @RequestBody WxPhoneLoginRequest request) {
        // 微信 code 换手机号及第三方错误转换由 Service/Client 层负责。
        return Result.success(authService.wxPhoneLogin(request));
    }

    /**
     * 当前登录用户首次设置密码。
     *
     * @param authorization 当前 access token 的 Bearer 请求头
     * @param request 初始明文密码；服务端仅持久化 BCrypt 哈希
     */
    @PostMapping("/set-password")
    public Result<Void> setPassword(@RequestHeader("Authorization") String authorization,
                                    @Valid @RequestBody SetPasswordRequest request) {
        // 完整 Authorization 头传给 Service，再由 TokenStore 校验并提取当前 userId。
        authService.setPassword(authorization, request);
        // 无业务响应体时仍使用统一成功 Result，保持前端响应结构一致。
        return Result.success();
    }

    /**
     * 小程序邀请码引导页一旦展示即标记完成，中途退出后不再强制进入。
     *
     * @param authorization 当前 access token 的 Bearer 请求头
     */
    @PostMapping("/mini-onboarding/invite-viewed")
    public Result<Void> completeMiniInviteOnboarding(@RequestHeader("Authorization") String authorization) {
        // userId 只从 Bearer Token 获取，请求体不能指定或伪造其他用户。
        authService.completeMiniInviteOnboarding(authorization);
        return Result.success();
    }

    /**
     * App 驾驶端手机号验证码登录；与小程序共用同一 userId。
     *
     * @param request 手机号、验证码、设备信息和可选注册来源
     */
    @PostMapping("/app/login")
    public Result<LoginResponse> appLogin(@Valid @RequestBody AppLoginRequest request) {
        // App 设备字段随请求交给 Service，用于设备绑定和登录审计。
        return Result.success(authService.appLogin(request));
    }

    /**
     * 退出登录；清理当前 access token 在 Redis 中的登录态。
     *
     * @param authorization 待撤销 access token 的 Bearer 请求头
     */
    @PostMapping("/logout")
    public Result<LogoutResponse> logout(@RequestHeader("Authorization") String authorization) {
        // Service 使用原始 access token 精确撤销 Redis JTI，并记录退出审计。
        return Result.success(authService.logout(authorization));
    }

    /**
     * 查询当前登录用户的基础认证信息。
     *
     * @param authorization 当前 access token 的 Bearer 请求头
     */
    @GetMapping("/me")
    public Result<CurrentUserResponse> getCurrentUser(@RequestHeader("Authorization") String authorization) {
        // 返回认证域最小资料；昵称、头像等用户资料不从本控制器暴露。
        return Result.success(authService.getCurrentUser(authorization));
    }

    /**
     * 使用 refresh token 轮换新的 access/refresh 令牌对。
     *
     * @param request 当前 refresh token
     * @return 新令牌对；旧 refresh token 已立即失效
     */
    @PostMapping("/refresh-token")
    public Result<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        // 刷新成功会轮换 access/refresh 两个令牌，旧 refresh token 立即失效。
        return Result.success(authService.refreshToken(request));
    }
}
