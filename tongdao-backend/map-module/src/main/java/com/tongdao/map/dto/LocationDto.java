package com.tongdao.map.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 地点坐标 DTO。
 *
 * <p>用于路线起终点、途经点和地点解析接口。</p>
 */
public record LocationDto(
        /** 地点名称。 */
        @Size(max = 128) String name,
        /** 地点地址。 */
        @Size(max = 255) String address,
        /** 纬度。 */
        @NotNull BigDecimal latitude,
        /** 经度。 */
        @NotNull BigDecimal longitude
) {
}
