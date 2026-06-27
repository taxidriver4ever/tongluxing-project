package com.tongdao.admin.vo;

import java.util.List;

/**
 * 通用分页响应。
 *
 * @param <T> 当前页记录类型
 */
public record PageResult<T>(
        /** 当前页记录列表。 */
        List<T> records,
        /** 符合条件的总记录数。 */
        long total,
        /** 当前页码。 */
        int page,
        /** 每页条数。 */
        int size
) {
}
