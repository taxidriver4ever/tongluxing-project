package com.tongdao.auth.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.tongdao.auth.dto.LoginRequest;
import com.tongdao.auth.dto.RefreshTokenRequest;
import com.tongdao.auth.dto.SmsCodeRequest;
import com.tongdao.auth.service.AuthService;
import com.tongdao.auth.vo.CurrentUserResponse;
import com.tongdao.auth.vo.LoginResponse;
import com.tongdao.auth.vo.LogoutResponse;
import com.tongdao.auth.vo.RefreshTokenResponse;
import com.tongdao.auth.vo.SmsCodeResponse;
import com.tongdao.common.result.Result;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/auth")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/sms-code")
    public Result<SmsCodeResponse> sendSmsCode(@Valid @RequestBody SmsCodeRequest request) {
        return Result.success(authService.sendSmsCode(request));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/logout")
    public Result<LogoutResponse> logout(@RequestHeader("Authorization") String authorization) {
        return Result.success(authService.logout(authorization));
    }

    @GetMapping("/me")
    public Result<CurrentUserResponse> getCurrentUser(@RequestHeader("Authorization") String authorization) {
        return Result.success(authService.getCurrentUser(authorization));
    }

    @PostMapping("/refresh-token")
    public Result<RefreshTokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refreshToken(request));
    }
}
