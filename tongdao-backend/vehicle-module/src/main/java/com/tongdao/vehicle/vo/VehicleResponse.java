package com.tongdao.vehicle.vo;

public record VehicleResponse(
        Long vehicleId,
        Long userId,
        String plateNoMask,
        String brand,
        String model,
        String vehicleType,
        String color,
        Integer seatCount,
        String energyType,
        String vehiclePhotoImageKey,
        String certificationStatus,
        Boolean isDefault
) {
}
