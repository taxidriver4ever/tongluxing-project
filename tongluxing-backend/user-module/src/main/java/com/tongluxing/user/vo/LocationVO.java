package com.tongluxing.user.vo;

import java.math.BigDecimal;

/**
 * 地点展示对象。
 *
 * <p>与 LocationRequest 分离，返回模型不携带输入校验注解。</p>
 *
 * @param name 地点简称
 * @param address 完整地址
 * @param latitude 纬度
 * @param longitude 经度
 */
public record LocationVO(String name, String address, BigDecimal latitude, BigDecimal longitude) {
}

