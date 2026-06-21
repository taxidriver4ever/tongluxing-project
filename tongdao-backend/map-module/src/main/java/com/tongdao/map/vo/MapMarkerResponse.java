package com.tongdao.map.vo;

import java.math.BigDecimal;

public record MapMarkerResponse(
        String markerId,
        String markerType,
        String title,
        String subtitle,
        BigDecimal latitude,
        BigDecimal longitude
) {
}
