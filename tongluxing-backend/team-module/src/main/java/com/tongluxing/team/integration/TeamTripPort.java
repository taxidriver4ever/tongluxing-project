package com.tongluxing.team.integration;

import java.time.LocalDateTime;

/**
 * 车队模块访问行程模块的跨模块端口。
 */
public interface TeamTripPort {

    /** 查询创建车队、入队校验所需的行程摘要。 */
    TeamTripDTO getTrip(Long tripId);

    /** 查询用户作为发布者拥有的进行中行程 ID。 */
    Long findRunningOwnedTripId(Long userId);

    /** 车队模块依赖的行程最小字段集合。 */
    record TeamTripDTO(
            Long tripId,
            Long ownerUserId,
            String startName,
            String endName,
            LocalDateTime departureTime,
            String status
    ) {
        public boolean running() {
            return "RUNNING".equals(status) || "ONGOING".equals(status);
        }
    }
}
