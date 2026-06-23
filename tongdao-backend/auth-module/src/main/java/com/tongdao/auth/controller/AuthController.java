package com.tongdao.auth.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.tongdao.auth.dto.LoginRequest;
import com.tongdao.auth.dto.RefreshTokenRequest;
import com.tongdao.auth.dto.SmsCodeRequest;
import com.tongdao.auth.dto.WxPhoneLoginRequest;
import com.tongdao.auth.service.AuthService;
import com.tongdao.auth.vo.CurrentUserResponse;
import com.tongdao.auth.vo.LoginResponse;
import com.tongdao.auth.vo.LogoutResponse;
import com.tongdao.auth.vo.RefreshTokenResponse;
import com.tongdao.auth.vo.SmsCodeResponse;
import com.tongdao.common.result.Result;

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

    /** 微信小程序手机号授权登录；服务端通过微信 code 换取手机号。 */
    @PostMapping("/wx-phone-login")
    public Result<LoginResponse> wxPhoneLogin(@Valid @RequestBody WxPhoneLoginRequest request) {
        return Result.success(authService.wxPhoneLogin(request));
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
