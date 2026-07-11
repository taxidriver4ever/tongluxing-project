package com.tongluxing.customerservice.vo;

import java.util.List;

/**
 * 简单分页结果。
 *
 * @param records 当前页数据
 * @param total 总记录数
 * @param page 当前页码，从 1 开始
 * @param size 当前页大小
 * @param <T> 记录类型
 */
public record PageResult<T>(List<T> records, long total, int page, int size) {
}
