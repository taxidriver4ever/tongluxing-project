package com.tongluxing.vehicle.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.vehicle.dto.VehicleAuthSubmitRequest;
import com.tongluxing.vehicle.service.VehicleService;
import com.tongluxing.vehicle.vo.VehicleAuthEligibilityResponse;
import com.tongluxing.vehicle.vo.VehicleAuthStatusResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 用户侧车辆认证闭环兼容接口。 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/vehicle/auth")
public class VehicleAuthController {
    /** 车辆认证业务服务，统一处理顺序校验、去重、入库和审核。 */
    private final VehicleService vehicleService;

    /** 保存车辆、行驶证及车辆照片，并创建 PENDING 申请；驾驶证走用户模块独立认证。 */
    @PostMapping("/submit")
    public Result<VehicleAuthStatusResponse> submit(@Valid @RequestBody VehicleAuthSubmitRequest request) {
        // 闭环接口会先创建或复用车辆档案，再复用标准认证提交流程。
        return Result.success(vehicleService.submitVehicleAuth(request));
    }

    /** 提交前按车牌检查是否已存在通过认证的相同车辆。 */
    @GetMapping("/eligibility")
    public Result<VehicleAuthEligibilityResponse> eligibility(@RequestParam String plateNumber) {
        // 返回“是否允许 + 当前状态 + 提示语”，便于前端在上传大图前提前阻断。
        return Result.success(vehicleService.checkVehicleAuthEligibility(plateNumber));
    }

    /** 查询当前用户最近一次车辆认证状态。 */
    @GetMapping("/status")
    public Result<VehicleAuthStatusResponse> status() {
        // 不接收用户参数，始终查询当前登录用户的最新申请。
        return Result.success(vehicleService.getMyVehicleAuthStatus());
    }
}
