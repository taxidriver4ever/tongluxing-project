package com.tongluxing.trip.service;

import com.tongluxing.trip.dto.TripTrackReviewRequest;
import com.tongluxing.trip.vo.TripSettlementResponse;
import com.tongluxing.trip.vo.TripTrackReviewResponse;

/** 行程结束后的独立成长值结算服务。 */
public interface TripSettlementService {
    /** 将本人 FINISHED 行程幂等结算为 SETTLED。 */
    TripSettlementResponse settle(Long tripId);

    /** 管理员审核中高风险轨迹，按审核后有效里程幂等发放或驳回成长值。 */
    TripTrackReviewResponse reviewTrack(Long tripId, Long operatorId, TripTrackReviewRequest request);
}
