package com.tongluxing.drivertrack.service;

import com.tongluxing.drivertrack.vo.MileageSettlementResponse;

/**
 * 行程里程结算服务。
 *
 * <p>输入累计有效里程，按结算阶段幂等发放成长值。里程来源可以是测试数据、
 * 规划路线距离或未来 driver-track GPS 实际里程。</p>
 */
public interface MileageSettlementService {

    /**
     * 按累计有效里程结算成长值。
     *
     * @param tripId 行程 ID
     * @param userId 获得成长值的用户 ID
     * @param distanceMeters 当前行程累计有效里程，单位米
     */
    MileageSettlementResponse settleMileage(Long tripId, Long userId, Integer distanceMeters);
}
