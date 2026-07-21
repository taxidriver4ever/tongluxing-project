package com.tongluxing.vehicle.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.vehicle.dto.VehicleAuthSubmitRequest;
import com.tongluxing.vehicle.service.VehicleService;
import com.tongluxing.vehicle.vo.VehicleAuthStatusResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 用户侧车辆认证闭环兼容接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/vehicle/auth")
public class VehicleAuthController {
    private final VehicleService vehicleService;

    /** 保存车辆、行驶证及车辆照片，并创建 PENDING 申请；驾驶证走用户模块独立认证。 */
    @PostMapping("/submit")
    public Result<VehicleAuthStatusResponse> submit(@Valid @RequestBody VehicleAuthSubmitRequest request) {
        return Result.success(vehicleService.submitVehicleAuth(request));
    }

    /** 查询当前用户最近一次车辆认证状态。 */
    @GetMapping("/status")
    public Result<VehicleAuthStatusResponse> status() {
        return Result.success(vehicleService.getMyVehicleAuthStatus());
    }
}
