package com.tongdao.vehicle.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.common.result.Result;
import com.tongdao.vehicle.dto.CreateVehicleRequest;
import com.tongdao.vehicle.dto.SubmitVehicleCertificationRequest;
import com.tongdao.vehicle.dto.UpdateVehicleRequest;
import com.tongdao.vehicle.service.VehicleService;
import com.tongdao.vehicle.vo.PublicVehicleCardResponse;
import com.tongdao.vehicle.vo.VehicleCertificationResponse;
import com.tongdao.vehicle.vo.VehicleListResponse;
import com.tongdao.vehicle.vo.VehicleResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 车辆模块接口。
 *
 * <p>负责当前用户车辆档案、默认车辆、车辆认证和公开车辆卡片等能力。
 * 控制层只做请求接收与响应包装，具体业务规则由 {@link VehicleService} 处理。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/vehicles")
public class VehicleController {

    /** 车辆业务服务。 */
    private final VehicleService vehicleService;

    /** 查询当前登录用户的车辆列表。 */
    @GetMapping("/me")
    public Result<VehicleListResponse> getMyVehicles() {
        return Result.success(vehicleService.getMyVehicles());
    }

    /** 创建当前用户的一辆车辆档案。 */
    @PostMapping
    public Result<VehicleResponse> createVehicle(@Valid @RequestBody CreateVehicleRequest request) {
        return Result.success(vehicleService.createVehicle(request));
    }

    /** 查询当前用户名下指定车辆详情。 */
    @GetMapping("/{vehicleId}")
    public Result<VehicleResponse> getVehicle(@PathVariable Long vehicleId) {
        return Result.success(vehicleService.getVehicle(vehicleId));
    }

    /** 更新当前用户名下指定车辆的基础资料。 */
    @PutMapping("/{vehicleId}")
    public Result<VehicleResponse> updateVehicle(
            @PathVariable Long vehicleId,
            @Valid @RequestBody UpdateVehicleRequest request
    ) {
        return Result.success(vehicleService.updateVehicle(vehicleId, request));
    }

    /** 逻辑删除当前用户名下指定车辆。 */
    @DeleteMapping("/{vehicleId}")
    public Result<Void> deleteVehicle(@PathVariable Long vehicleId) {
        vehicleService.deleteVehicle(vehicleId);
        return Result.success();
    }

    /** 将当前用户名下指定车辆设置为默认车辆。 */
    @PutMapping("/{vehicleId}/default")
    public Result<VehicleResponse> setDefaultVehicle(@PathVariable Long vehicleId) {
        return Result.success(vehicleService.setDefaultVehicle(vehicleId));
    }

    /** 提交当前用户名下指定车辆的认证资料。 */
    @PostMapping("/{vehicleId}/certification")
    public Result<VehicleCertificationResponse> submitCertification(
            @PathVariable Long vehicleId,
            @Valid @RequestBody SubmitVehicleCertificationRequest request
    ) {
        return Result.success(vehicleService.submitCertification(vehicleId, request));
    }

    /** 查询当前用户名下指定车辆最近一次认证记录。 */
    @GetMapping("/{vehicleId}/certification")
    public Result<VehicleCertificationResponse> getCertification(@PathVariable Long vehicleId) {
        return Result.success(vehicleService.getCertification(vehicleId));
    }

    /** 查询车辆公开展示卡片，供用户主页、行程等场景展示车辆摘要信息。 */
    @GetMapping("/{vehicleId}/public-card")
    public Result<PublicVehicleCardResponse> getPublicCard(@PathVariable Long vehicleId) {
        return Result.success(vehicleService.getPublicCard(vehicleId));
    }
}
