package com.tongluxing.notify.vo;

import java.util.List;

/**
 * 简单分页结果。
 */
public record PageResult<T>(List<T> records, long total, int page, int size) {
}
