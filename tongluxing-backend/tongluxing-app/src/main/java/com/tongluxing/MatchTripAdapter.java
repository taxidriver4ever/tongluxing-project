package com.tongluxing;

import java.util.List;

import org.springframework.stereotype.Component;

import com.tongluxing.match.integration.MatchTripPort;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.mapper.TripMapper;

import lombok.RequiredArgsConstructor;

/**
 * 匹配模块访问行程模块的适配器。
 *
 * <p>应用层负责把 trip-module 的实体数据转换为 match-module 的行程端口 DTO。</p>
 */
@Component
@RequiredArgsConstructor
public class MatchTripAdapter implements MatchTripPort {
    private final TripMapper tripMapper;

    /**
     * 按行程 ID 查询用于匹配计算的行程摘要。
     *
     * @param tripId 行程 ID
     * @return 匹配模块行程摘要；不存在时返回 null
     */
    @Override
    public MatchTripDTO getTrip(Long tripId) {
        Trip trip = tripMapper.findById(tripId);
        return trip == null ? null : toDTO(trip);
    }

    /**
     * 查询公开行程列表，作为匹配推荐候选池。
     *
     * @param limit 最大查询数量
     * @return 匹配模块行程摘要列表
     */
    @Override
    public List<MatchTripDTO> listPublicTrips(int limit) {
        return tripMapper.findPublicTrips(limit).stream()
                .map(this::toDTO)
                .toList();
    }

    /**
     * 将行程实体转换为匹配模块所需的最小字段集合。
     *
     * @param trip 行程实体
     * @return 匹配模块行程 DTO
     */
    private MatchTripDTO toDTO(Trip trip) {
        return new MatchTripDTO(trip.getId(), trip.getUserId(), trip.getStartName(), trip.getEndName(),
                trip.getDepartureTime(), trip.getTravelDepth());
    }
}
