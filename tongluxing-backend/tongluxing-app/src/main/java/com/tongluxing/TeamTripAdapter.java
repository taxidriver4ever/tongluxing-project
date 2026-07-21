package com.tongluxing;

import org.springframework.stereotype.Component;

import com.tongluxing.team.integration.TeamTripPort;
import com.tongluxing.trip.entity.Trip;
import com.tongluxing.trip.mapper.TripMapper;

import lombok.RequiredArgsConstructor;

/**
 * 车队模块访问行程模块的适配器。
 *
 * <p>建队时通过该适配器校验行程归属并读取行程摘要。</p>
 */
@Component
@RequiredArgsConstructor
public class TeamTripAdapter implements TeamTripPort {

    private final TripMapper tripMapper;

    /**
     * 查询创建车队所需的行程摘要。
     *
     * @param tripId 行程 ID
     * @return 车队模块行程摘要；不存在时返回 null
     */
    @Override
    public Long findActiveOwnedTripId(Long userId) {
        return tripMapper.findActiveTripIdByUserId(userId);
    }

    @Override
    public TeamTripDTO getTrip(Long tripId) {
        Trip trip = tripMapper.findById(tripId);
        if (trip == null) {
            return null;
        }
        return new TeamTripDTO(trip.getId(), trip.getUserId(), trip.getStartName(), trip.getEndName(),
                trip.getDepartureTime());
    }
}
