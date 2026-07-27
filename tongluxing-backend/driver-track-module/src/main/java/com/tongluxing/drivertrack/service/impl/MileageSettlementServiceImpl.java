package com.tongluxing.drivertrack.service.impl;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.drivertrack.entity.DriverTrackDistanceRecord;
import com.tongluxing.drivertrack.mapper.DriverTrackDistanceRecordMapper;
import com.tongluxing.drivertrack.service.MileageSettlementService;
import com.tongluxing.drivertrack.vo.MileageSettlementResponse;

import lombok.RequiredArgsConstructor;

/**
 * 行程里程结算服务实现。
 */
@Service
@RequiredArgsConstructor
public class MileageSettlementServiceImpl implements MileageSettlementService {

    private static final String SETTLE_TYPE_WAYPOINT = "WAYPOINT";

    private final DriverTrackDistanceRecordMapper distanceMapper;

    @Override
    @Transactional
    public MileageSettlementResponse settleMileage(Long tripId, Long userId, Integer distanceMeters) {
        int effectiveDistance = distanceMeters == null ? 0 : Math.max(0, distanceMeters);
        // 修订版规则：上传途中只记录，不实时发成长值。最终由行程结算一次性按
        // floor(settlementDistance / 5000) * 1 发给所有有效成员，且不足 5km 不跨行程累计。
        return new MileageSettlementResponse(
                String.valueOf(tripId),
                String.valueOf(userId),
                effectiveDistance,
                0,
                0,
                false
        );
    }

    @Override
    @Transactional
    public MileageSettlementResponse settleWaypoint(Long tripId, Long userId, Long waypointId,
                                                     String waypointName, Integer distanceMeters) {
        int effectiveDistance = distanceMeters == null ? 0 : Math.max(0, distanceMeters);
        String settleKey = tripId + ":" + userId + ":TRIP_WAYPOINT:" + waypointId;
        if (distanceMapper.findBySettleKey(settleKey) != null) {
            return new MileageSettlementResponse(String.valueOf(tripId), String.valueOf(userId),
                    effectiveDistance, 0, 0, true);
        }
        // 途经点只记录到达事实，不发成长值；成长值在最终结算按每 5 公里 1 点统一发放。
        insertSettlement(tripId, userId, effectiveDistance, effectiveDistance,
                SETTLE_TYPE_WAYPOINT, settleKey);
        return new MileageSettlementResponse(String.valueOf(tripId), String.valueOf(userId),
                effectiveDistance, 0, 0, false);
    }

    private void insertSettlement(Long tripId, Long userId, int totalDistance, int stageDistance,
                                  String settleType, String settleKey) {
        LocalDateTime now = LocalDateTime.now();
        DriverTrackDistanceRecord record = new DriverTrackDistanceRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setTripId(tripId);
        record.setDriverId(userId);
        record.setTotalDistance(totalDistance);
        record.setLastSettleDistance(stageDistance);
        record.setSettleType(settleType);
        record.setSettleKey(settleKey);
        record.setSettleTime(now);
        record.setEventPublished(1);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setDeleted(0);
        distanceMapper.insert(record);
    }

}
