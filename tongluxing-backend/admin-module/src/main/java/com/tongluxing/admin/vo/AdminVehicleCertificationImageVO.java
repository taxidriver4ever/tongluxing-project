package com.tongluxing.admin.vo;

/** 后台车辆认证图片，包含原始 Key 和短期访问地址。 */
public record AdminVehicleCertificationImageVO(String imageType, String imageKey, String imageUrl) {
}
