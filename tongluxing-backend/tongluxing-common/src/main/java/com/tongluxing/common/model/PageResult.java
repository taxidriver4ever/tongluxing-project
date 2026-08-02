package com.tongluxing.common.model;

import java.util.List;

/**
 * 通用分页返回结构。
 *
 * <p>page 使用从 1 开始的业务页码；records 只包含当前页，total 表示相同过滤条件
 * 下的全部记录数。</p>
 *
 * @param records 当前页记录
 * @param total 总记录数
 * @param page 当前页码
 * @param size 每页数量
 */
public record PageResult<T>(List<T> records, long total, int page, int size) {
}

