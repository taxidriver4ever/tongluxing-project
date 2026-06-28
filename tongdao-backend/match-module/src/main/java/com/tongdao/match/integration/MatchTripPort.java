package com.tongdao.match.integration;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 匹配模块访问行程数据的跨模块端口。
 */
public interface MatchTripPort {

    /**
     * 根据行程 ID 获取用于匹配计算的行程摘要。
     */
    MatchTripDTO getTrip(Long tripId);

    /**
     * 查询公开行程列表，作为推荐候选池。
     */
    List<MatchTripDTO> listPublicTrips(int limit);

    /**
     * 行程匹配所需的最小字段集合。
     */
    record MatchTripDTO(
            Long tripId,
            Long userId,
            String startName,
            String endName,
            LocalDateTime departureTime,
            String travelDepth
    ) {
    }
}
