package com.tongdao.order.vo;

import java.util.List;
/**
 * PageResult 分页响应对象。
 */

public record PageResult<T>(List<T> records, long total, int page, int size) {
}

