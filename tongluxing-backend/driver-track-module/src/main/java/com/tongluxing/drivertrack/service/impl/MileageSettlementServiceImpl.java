package com.tongluxing.drivertrack.service.impl;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.drivertrack.entity.DriverTrackDistanceRecord;
import com.tongluxing.drivertrack.integration.DriverTrackGrowthPort;
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

    private static final String BIZ_TYPE_TRIP_MILEAGE = "TRIP_MILEAGE";
    private static final String SETTLE_TYPE_MILESTONE = "MILEAGE_STAGE";

    private final DriverTrackDistanceRecordMapper distanceMapper;
    private final DriverTrackGrowthPort growthPort;

    @Value("${mileage.settlement.stage-meters:50000}")
    private Integer stageMeters;

    @Value("${mileage.growth.points-per-km:1}")
    private Integer pointsPerKm;

    @Override
    @Transactional
    public MileageSettlementResponse settleMileage(Long tripId, Long userId, Integer distanceMeters) {
        int effectiveDistance = distanceMeters == null ? 0 : Math.max(0, distanceMeters);
        int normalizedStageMeters = stageMeters == null || stageMeters <= 0 ? 50_000 : stageMeters;
        int stageCount = effectiveDistance / normalizedStageMeters;
        int settledStages = 0;
        int grantedPoints = 0;

        for (int stage = 1; stage <= stageCount; stage++) {
            int stageDistance = stage * normalizedStageMeters;
            String settleKey = settleKey(tripId, userId, stageDistance);
            if (distanceMapper.findBySettleKey(settleKey) != null) {
                continue;
            }
            int points = calculatePoints(normalizedStageMeters);
            insertSettlement(tripId, userId, effectiveDistance, stageDistance, settleKey);
            growthPort.grantMileageGrowth(userId, settleKey, points, remark(stageDistance));
            settledStages++;
            grantedPoints += points;
        }

        return new MileageSettlementResponse(
                String.valueOf(tripId),
                String.valueOf(userId),
                effectiveDistance,
                settledStages,
                grantedPoints,
                settledStages == 0 && stageCount > 0
        );
    }

    private void insertSettlement(Long tripId, Long userId, int totalDistance, int stageDistance, String settleKey) {
        LocalDateTime now = LocalDateTime.now();
        DriverTrackDistanceRecord record = new DriverTrackDistanceRecord();
        record.setId(SnowflakeIdGenerator.nextId());
        record.setTripId(tripId);
        record.setDriverId(userId);
        record.setTotalDistance(totalDistance);
        record.setLastSettleDistance(stageDistance);
        record.setSettleType(SETTLE_TYPE_MILESTONE);
        record.setSettleKey(settleKey);
        record.setSettleTime(now);
        record.setEventPublished(1);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);
        record.setDeleted(0);
        distanceMapper.insert(record);
    }

    private int calculatePoints(int settledDistanceMeters) {
        int normalizedPointsPerKm = pointsPerKm == null || pointsPerKm <= 0 ? 1 : pointsPerKm;
        return Math.max(1, (settledDistanceMeters / 1000) * normalizedPointsPerKm);
    }

    private String settleKey(Long tripId, Long userId, int stageDistance) {
        return tripId + ":" + userId + ":" + BIZ_TYPE_TRIP_MILEAGE + ":" + stageDistance;
    }

    private String remark(int stageDistance) {
        return "完成" + (stageDistance / 1000) + "公里驾驶";
    }
}
