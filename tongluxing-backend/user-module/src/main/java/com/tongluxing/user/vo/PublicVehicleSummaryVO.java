package com.tongluxing.user.vo;

/**
 * 公开主页展示的用户主车辆摘要，不包含车牌号、车架号等敏感车辆信息。
 *
 * @param brand 车辆品牌
 * @param model 车型
 * @param vehicleType 车辆类型
 */
public record PublicVehicleSummaryVO(String brand, String model, String vehicleType) {
}

