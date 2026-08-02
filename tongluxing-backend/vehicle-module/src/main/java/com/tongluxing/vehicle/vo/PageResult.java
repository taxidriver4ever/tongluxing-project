package com.tongluxing.vehicle.vo;

import java.util.List;

/**
 * 车辆模块通用分页结果。
 *
 * @param records 当前页记录，不包含其他页数据
 * @param total 符合当前筛选条件的总记录数
 * @param page 经服务端规范化后的页码，从 1 开始
 * @param size 经服务端限制后的每页数量
 */
public record PageResult<T>(List<T> records, long total, int page, int size) {
}
