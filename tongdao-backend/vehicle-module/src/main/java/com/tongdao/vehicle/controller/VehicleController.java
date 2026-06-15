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

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/vehicles")
public class VehicleController {

    private final VehicleService vehicleService;

    @GetMapping("/me")
    public Result<VehicleListResponse> getMyVehicles() {
        return Result.success(vehicleService.getMyVehicles());
    }

    @PostMapping
    public Result<VehicleResponse> createVehicle(@Valid @RequestBody CreateVehicleRequest request) {
        return Result.success(vehicleService.createVehicle(request));
    }

    @GetMapping("/{vehicleId}")
    public Result<VehicleResponse> getVehicle(@PathVariable Long vehicleId) {
        return Result.success(vehicleService.getVehicle(vehicleId));
    }

    @PutMapping("/{vehicleId}")
    public Result<VehicleResponse> updateVehicle(
            @PathVariable Long vehicleId,
            @Valid @RequestBody UpdateVehicleRequest request
    ) {
        return Result.success(vehicleService.updateVehicle(vehicleId, request));
    }

    @DeleteMapping("/{vehicleId}")
    public Result<Void> deleteVehicle(@PathVariable Long vehicleId) {
        vehicleService.deleteVehicle(vehicleId);
        return Result.success();
    }

    @PutMapping("/{vehicleId}/default")
    public Result<VehicleResponse> setDefaultVehicle(@PathVariable Long vehicleId) {
        return Result.success(vehicleService.setDefaultVehicle(vehicleId));
    }

    @PostMapping("/{vehicleId}/certification")
    public Result<VehicleCertificationResponse> submitCertification(
            @PathVariable Long vehicleId,
            @Valid @RequestBody SubmitVehicleCertificationRequest request
    ) {
        return Result.success(vehicleService.submitCertification(vehicleId, request));
    }

    @GetMapping("/{vehicleId}/certification")
    public Result<VehicleCertificationResponse> getCertification(@PathVariable Long vehicleId) {
        return Result.success(vehicleService.getCertification(vehicleId));
    }

    @GetMapping("/{vehicleId}/public-card")
    public Result<PublicVehicleCardResponse> getPublicCard(@PathVariable Long vehicleId) {
        return Result.success(vehicleService.getPublicCard(vehicleId));
    }
}
