package com.tongluxing.user.dto.request;

import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 地点请求对象，用于行程起点、终点和途经点。
 *
 * <p>经纬度采用 WGS84 数值范围校验：纬度 -90~90，经度 -180~180。</p>
 *
 * @param name 地点简称
 * @param address 完整地址
 * @param latitude 纬度
 * @param longitude 经度
 */
public record LocationRequest(
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Size(max = 255) String address,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude
) {
}

