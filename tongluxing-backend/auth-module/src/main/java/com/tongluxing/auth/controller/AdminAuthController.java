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

/** Web Admin 独立登录、会话检查与退出接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/auth")
public class AdminAuthController {
    private final AdminAuthService adminAuthService;

    @PostMapping("/login")
    public Result<AdminLoginResponse> login(@Valid @RequestBody AdminLoginRequest request) {
        return Result.success(adminAuthService.login(request));
    }

    @GetMapping("/me")
    public Result<AdminCurrentOperatorResponse> current(
            @RequestHeader("Authorization") String authorization) {
        return Result.success(adminAuthService.current(authorization));
    }

    @PostMapping("/logout")
    public Result<AdminLogoutResponse> logout(
            @RequestHeader("Authorization") String authorization) {
        return Result.success(adminAuthService.logout(authorization));
    }
}
