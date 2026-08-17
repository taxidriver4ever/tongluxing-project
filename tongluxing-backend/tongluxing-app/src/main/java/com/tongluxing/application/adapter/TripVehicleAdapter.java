package com.tongluxing.application.adapter;

import java.util.Comparator;

import org.springframework.stereotype.Component;

import com.tongluxing.trip.integration.TripVehiclePort;
import com.tongluxing.vehicle.entity.VehicleProfile;
import com.tongluxing.vehicle.mapper.VehicleProfileMapper;

import lombok.RequiredArgsConstructor;

/**
 * 行程模块访问车辆模块的适配器。
 *
 * <p>用于发布行程时校验车辆归属和认证状态。</p>
 */
@Component
@RequiredArgsConstructor
public class TripVehicleAdapter implements TripVehiclePort {
    private final VehicleProfileMapper vehicleProfileMapper;

    /**
     * 查询指定用户名下的车辆，并返回行程模块需要的车辆摘要。
     *
     * @param vehicleId 车辆 ID
     * @param userId 用户 ID
     * @return 车辆摘要；不存在时返回 null
     */
    @Override
    public TripVehicleDTO getCertifiedVehicle(Long vehicleId, Long userId) {
        VehicleProfile vehicle = vehicleProfileMapper.findByIdAndUserId(vehicleId, userId);
        if (vehicle == null) {
            return null;
        }
        return new TripVehicleDTO(vehicle.getId(), vehicle.getBrand(), vehicle.getModel(),
                vehicle.getCertificationStatus());
    }

    @Override
    public TripVehicleDTO getDefaultCertifiedVehicle(Long userId) {
        return vehicleProfileMapper.findByUserId(userId).stream()
                .filter(vehicle -> "APPROVED".equals(vehicle.getCertificationStatus()))
                .min(Comparator.comparing(vehicle -> vehicle.getDefaultFlag() == null || vehicle.getDefaultFlag() != 1))
                .map(vehicle -> new TripVehicleDTO(vehicle.getId(), vehicle.getBrand(), vehicle.getModel(),
                        vehicle.getCertificationStatus()))
                .orElse(null);
    }
}
