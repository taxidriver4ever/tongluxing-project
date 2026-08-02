package com.tongluxing.vehicle.vo;

/**
 * 车辆认证图片响应。
 *
 * @param imageType 图片业务类型，例如行驶证或车辆外观图
 * @param imageKey 对象存储资源标识或已保存 URL
 */
public record VehicleCertificationImageVO(String imageType, String imageKey) {
}
