package com.tongluxing.drivertrack.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.drivertrack.mapper.TripTrackRiskMapper;

import lombok.RequiredArgsConstructor;

/**
 * 轨迹安全审计独立事务。
 *
 * <p>非法成员、非进行中行程等请求随后会抛出业务异常，因此审计记录必须使用
 * REQUIRES_NEW，避免随上传主事务回滚。</p>
 */
@Service
@RequiredArgsConstructor
public class TripTrackSecurityAuditService {
    private final TripTrackRiskMapper riskMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long tripId, Long userId, String type, String reason) {
        LocalDateTime now = LocalDateTime.now();
        String detail = "{\"reason\":\"" + jsonEscape(reason) + "\"}";
        riskMapper.insertAnomaly(
                SnowflakeIdGenerator.nextId(), tripId, userId, null, null,
                type, 2, detail, now, now);
    }

    private String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
