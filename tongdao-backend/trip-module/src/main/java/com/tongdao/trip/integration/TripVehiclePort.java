package com.tongdao.trip.integration;

public interface TripVehiclePort {

    TripVehicleDTO getCertifiedVehicle(Long vehicleId, Long userId);

    record TripVehicleDTO(
            Long vehicleId,
            String brand,
            String model,
            String certificationStatus
    ) {
    }
}
