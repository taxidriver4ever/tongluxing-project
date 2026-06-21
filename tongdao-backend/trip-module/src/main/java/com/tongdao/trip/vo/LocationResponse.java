package com.tongdao.trip.vo;

import java.math.BigDecimal;

public record LocationResponse(
        String name,
        String address,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
