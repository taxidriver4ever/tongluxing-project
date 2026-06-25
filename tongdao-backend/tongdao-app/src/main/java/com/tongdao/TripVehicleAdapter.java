package com.tongdao;

import org.springframework.stereotype.Component;

import com.tongdao.trip.integration.TripVehiclePort;
import com.tongdao.vehicle.entity.VehicleProfile;
import com.tongdao.vehicle.mapper.VehicleProfileMapper;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TripVehicleAdapter implements TripVehiclePort {
    private final VehicleProfileMapper vehicleProfileMapper;

    @Override
    public TripVehicleDTO getCertifiedVehicle(Long vehicleId, Long userId) {
        VehicleProfile vehicle = vehicleProfileMapper.findByIdAndUserId(vehicleId, userId);
        if (vehicle == null) {
            return null;
        }
        return new TripVehicleDTO(vehicle.getId(), vehicle.getBrand(), vehicle.getModel(),
                vehicle.getCertificationStatus());
    }
}
