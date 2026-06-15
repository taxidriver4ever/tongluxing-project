package com.tongdao.vehicle.vo;

import java.util.List;

public record VehicleListResponse(
        List<VehicleResponse> vehicles
) {
}
