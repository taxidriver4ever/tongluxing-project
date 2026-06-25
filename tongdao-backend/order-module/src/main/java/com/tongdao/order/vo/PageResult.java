package com.tongdao.order.vo;

import java.util.List;

public record PageResult<T>(List<T> records, long total, int page, int size) {
}

