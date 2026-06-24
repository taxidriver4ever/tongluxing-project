package com.tongdao.merchant.vo;

import java.util.List;

/**
 * 通用分页返回结构。
 */
public record PageResult<T>(List<T> records, long total, int page, int size) {
}
