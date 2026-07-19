package com.tongluxing.trip.service;

import com.tongluxing.trip.vo.TripSettlementResponse;

/** 行程结束后的独立成长值结算服务。 */
public interface TripSettlementService {
    /** 将本人 FINISHED 行程幂等结算为 SETTLED。 */
    TripSettlementResponse settle(Long tripId);
}
