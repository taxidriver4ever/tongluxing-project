package com.tongdao.team.integration;

import java.time.LocalDateTime;

/**
 * 车队模块访问行程模块的跨模块端口。
 */
public interface TeamTripPort {

    /**
     * 查询创建车队所需的行程摘要。
     */
    TeamTripDTO getTrip(Long tripId);

    /**
     * 车队创建依赖的行程最小字段集合。
     */
    record TeamTripDTO(
            Long tripId,
            Long ownerUserId,
            String startName,
            String endName,
            LocalDateTime departureTime
    ) {
    }
}
