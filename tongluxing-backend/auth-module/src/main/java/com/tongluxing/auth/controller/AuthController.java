package com.tongluxing.auth.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.tongluxing.auth.dto.LoginRequest;
import com.tongluxing.auth.dto.AppBindByMiniTicketRequest;
import com.tongluxing.auth.dto.AppLoginRequest;
import com.tongluxing.auth.dto.PasswordLoginRequest;
import com.tongluxing.auth.dto.RefreshTokenRequest;
import com.tongluxing.auth.dto.SmsCodeRequest;
import com.tongluxing.auth.dto.SetPasswordRequest;
import com.tongluxing.auth.dto.WxPhoneLoginRequest;
import com.tongluxing.auth.service.AuthService;
import com.tongluxing.auth.vo.CurrentUserResponse;
import com.tongluxing.auth.vo.AppBindTicketResponse;
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
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/auth")
public class AuthController {

    /** 认证业务服务，封装登录校验、令牌签发、日志记录等核心流程。 */
    private final AuthService authService;

    /** 发送短信验证码，用于手机号验证码登录。 */
    @PostMapping("/sms-code")
    public Result<SmsCodeResponse> sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        return Result.success(authService.sendSmsCode(request));
    }

    /** 手机号 + 短信验证码登录；新用户会自动创建认证账号。 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /** 手机号 + 密码登录；不自动注册。 */
    @PostMapping("/password-login")
    public Result<LoginResponse> passwordLogin(@Valid @RequestBody PasswordLoginRequest request) {
        return Result.success(authService.passwordLogin(request));
    }

    /** 微信小程序手机号授权登录；服务端通过微信 code 换取手机号。 */
    @PostMapping("/wx-phone-login")
    public Result<LoginResponse> wxPhoneLogin(@Valid @RequestBody WxPhoneLoginRequest request) {
        return Result.success(authService.wxPhoneLogin(request));
    }

    /** 联调及正式账号初始化：当前登录用户首次设置密码。 */
    @PostMapping("/set-password")
    public Result<Void> setPassword(@RequestHeader("Authorization") String authorization,
                                    @Valid @RequestBody SetPasswordRequest request) {
        authService.setPassword(authorization, request);
        return Result.success();
    }

    /** App 驾驶端手机号验证码登录；与小程序共用同一 userId。 */
    @PostMapping("/app/login")
    public Result<LoginResponse> appLogin(@Valid @RequestBody AppLoginRequest request) {
        return Result.success(authService.appLogin(request));
    }

    /** 小程序端生成一次性 App 绑定 ticket。 */
    @PostMapping("/app/bind-ticket")
    public Result<AppBindTicketResponse> createAppBindTicket(@RequestHeader("Authorization") String authorization) {
        return Result.success(authService.createAppBindTicket(authorization));
    }

    /** App 使用小程序端 ticket 绑定并登录。 */
    @PostMapping("/app/bind-by-mini-ticket")
    public Result<LoginResponse> bindAppByMiniTicket(@Valid @RequestBody AppBindByMiniTicketRequest request) {
        return Result.success(authService.bindAppByMiniTicket(request));
    }

    /** 退出登录；清理当前 access token 在 Redis 中的登录态。 */
    @PostMapping("/logout")
    public Result<LogoutResponse> logout(@RequestHeader("Authorization") String authorization) {
        return Result.success(authService.logout(authorization));
    }

    /** 查询当前登录用户的基础信息。 */
    @GetMapping("/me")
    public Result<CurrentUserResponse> getCurrentUser(@RequestHeader("Authorization") String authorization) {
        return Result.success(authService.getCurrentUser(authorization));
    }

    /** 使用 refresh token 换取新的 access token。 */
    @PostMapping("/refresh-token")
    public Result<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshToken(request));
    }
}
