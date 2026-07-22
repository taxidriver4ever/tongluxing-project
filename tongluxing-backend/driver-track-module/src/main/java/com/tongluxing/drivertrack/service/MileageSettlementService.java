package com.tongluxing.drivertrack.service;

import com.tongluxing.drivertrack.vo.MileageSettlementResponse;

/** 行程实际里程与途经点分段结算服务。 */
public interface MileageSettlementService {

    /** 每累计 50 公里执行一次幂等成长值发放。 */
    MileageSettlementResponse settleMileage(Long tripId, Long userId, Integer distanceMeters);

    /** 到达途经点时执行一次幂等成长值发放。 */
    MileageSettlementResponse settleWaypoint(Long tripId, Long userId, Long waypointId,
                                              String waypointName, Integer distanceMeters);
}
