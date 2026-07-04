package com.tongluxing.vehicle.vo;

import java.util.List;

/**
 * 当前用户车辆列表响应。
 */
public record VehicleListResponse(
        /** 车辆列表，默认车辆通常排在前面。 */
        List<VehicleResponse> vehicles
) {
}
