package com.tongdao.vehicle.vo;

public record PublicVehicleCardResponse(
        Long vehicleId,
        String brand,
        String model,
        String vehicleType,
        String color,
        String plateNoMask,
        String certificationStatus,
        Boolean isDefault
) {
}
