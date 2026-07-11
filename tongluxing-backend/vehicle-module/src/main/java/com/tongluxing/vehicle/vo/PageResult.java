package com.tongluxing.vehicle.vo;

import java.util.List;

/** 车辆模块通用分页结果。 */
public record PageResult<T>(List<T> records, long total, int page, int size) {
}
