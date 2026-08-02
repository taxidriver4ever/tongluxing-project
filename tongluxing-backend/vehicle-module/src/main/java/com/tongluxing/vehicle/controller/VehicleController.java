package com.tongluxing.vehicle.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.vehicle.dto.CreateVehicleRequest;
import com.tongluxing.vehicle.dto.SubmitVehicleCertificationRequest;
import com.tongluxing.vehicle.dto.UpdateVehicleRequest;
import com.tongluxing.vehicle.service.VehicleService;
import com.tongluxing.vehicle.vo.PublicVehicleCardResponse;
import com.tongluxing.vehicle.vo.VehicleCertificationResponse;
import com.tongluxing.vehicle.vo.VehicleListResponse;
import com.tongluxing.vehicle.vo.VehicleResponse;

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
        // 用户身份由 Service 从认证上下文读取，Controller 不接收 userId。
        return Result.success(vehicleService.getMyVehicles());
    }

    /** 创建当前用户的一辆车辆档案。 */
    @PostMapping
    public Result<VehicleResponse> createVehicle(@Valid @RequestBody CreateVehicleRequest request) {
        // @Valid 在进入业务层前完成长度、座位数等基础格式校验。
        return Result.success(vehicleService.createVehicle(request));
    }

    /** 查询当前用户名下指定车辆详情。 */
    @GetMapping("/{vehicleId}")
    public Result<VehicleResponse> getVehicle(@PathVariable Long vehicleId) {
        // Service 会将 vehicleId 与当前用户一起查询，防止跨用户读取。
        return Result.success(vehicleService.getVehicle(vehicleId));
    }

    /** 更新当前用户名下指定车辆的基础资料。 */
    @PutMapping("/{vehicleId}")
    public Result<VehicleResponse> updateVehicle(
            @PathVariable Long vehicleId,
            @Valid @RequestBody UpdateVehicleRequest request
    ) {
        // 更新接口只处理可编辑的展示资料，不在 Controller 内拼装实体。
        return Result.success(vehicleService.updateVehicle(vehicleId, request));
    }

    /** 逻辑删除当前用户名下指定车辆。 */
    @DeleteMapping("/{vehicleId}")
    public Result<Void> deleteVehicle(@PathVariable Long vehicleId) {
        // Service 完成逻辑删除、状态限制、缓存清理和审计后再返回空成功体。
        vehicleService.deleteVehicle(vehicleId);
        return Result.success();
    }

    /** 删除被驳回车辆的认证历史，并从“我的车辆”中移除对应车辆卡片。 */
    @DeleteMapping("/{vehicleId}/rejected-certification-history")
    public Result<Void> deleteRejectedCertificationHistory(@PathVariable Long vehicleId) {
        // 该操作会删除驳回认证附件并移除车辆卡片，由事务保证一致性。
        vehicleService.deleteRejectedCertificationHistory(vehicleId);
        return Result.success();
    }

    /** 将当前用户名下指定车辆设置为默认车辆。 */
    @PutMapping("/{vehicleId}/default")
    public Result<VehicleResponse> setDefaultVehicle(@PathVariable Long vehicleId) {
        // 只有认证通过的车辆才能成为主要车辆，规则在 Service 统一执行。
        return Result.success(vehicleService.setDefaultVehicle(vehicleId));
    }

    /** 提交当前用户名下指定车辆的认证资料。 */
    @PostMapping({"/{vehicleId}/certification", "/{vehicleId}/certifications"})
    public Result<VehicleCertificationResponse> submitCertification(
            @PathVariable Long vehicleId,
            @Valid @RequestBody SubmitVehicleCertificationRequest request
    ) {
        // 保留 certification/certifications 两个路径是对已发布客户端的兼容。
        return Result.success(vehicleService.submitCertification(vehicleId, request));
    }

    /** 查询当前用户名下指定车辆最近一次认证记录。 */
    @GetMapping({"/{vehicleId}/certification", "/{vehicleId}/certifications"})
    public Result<VehicleCertificationResponse> getCertification(@PathVariable Long vehicleId) {
        // 没有历史记录时 Service 会返回 UNSUBMITTED，而不是 null。
        return Result.success(vehicleService.getCertification(vehicleId));
    }

    /** 查询车辆公开展示卡片，供用户主页、行程等场景展示车辆摘要信息。 */
    @GetMapping("/{vehicleId}/public-card")
    public Result<PublicVehicleCardResponse> getPublicCard(@PathVariable Long vehicleId) {
        // 公开卡片只含脱敏车牌和车型摘要，不返回证件明文。
        return Result.success(vehicleService.getPublicCard(vehicleId));
    }
}
