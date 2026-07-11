package com.tongluxing.groupbuy.vo;

import java.util.List;

/**
 * 通用分页响应对象。
 *
 * @param records 当前页数据
 * @param total 符合条件的总记录数
 * @param page 当前页码，从 1 开始
 * @param size 当前页大小
 * @param <T> 分页数据类型
 */
public record PageResult<T>(List<T> records, long total, int page, int size) {
}

