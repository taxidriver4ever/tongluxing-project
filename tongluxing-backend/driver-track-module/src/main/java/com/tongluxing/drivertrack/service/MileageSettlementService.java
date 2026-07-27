package com.tongluxing.drivertrack.service;

import com.tongluxing.drivertrack.vo.MileageSettlementResponse;

/** 行程实际里程与途经点分段结算服务。 */
public interface MileageSettlementService {

    /** 仅返回当前行程里程快照；成长值在最终结算时按每 5 公里 1 点统一发放。 */
    MileageSettlementResponse settleMileage(Long tripId, Long userId, Integer distanceMeters);

    /** 幂等记录途经点到达事实；途经点本身不再发放成长值。 */
    MileageSettlementResponse settleWaypoint(Long tripId, Long userId, Long waypointId,
                                              String waypointName, Integer distanceMeters);
}
