package com.tongluxing.trip.integration;

/**
 * 行程模块访问车辆模块的跨模块端口。
 */
public interface TripVehiclePort {

    /**
     * 查询指定用户拥有的车辆摘要。
     */
    TripVehicleDTO getCertifiedVehicle(Long vehicleId, Long userId);

    /** 查询当前用户默认或最先认证通过的车辆。 */
    TripVehicleDTO getDefaultCertifiedVehicle(Long userId);

    /**
     * 行程发布需要的车辆最小字段集合。
     */
    record TripVehicleDTO(
            Long vehicleId,
            String brand,
            String model,
            String certificationStatus
    ) {
    }
}
