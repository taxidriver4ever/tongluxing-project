package com.tongluxing.admin.vo;

import java.util.List;

/** 后台轨迹审核详情，包含队长轨迹汇总、成员最新位置、异常和队长轨迹点。 */
public record AdminTripTrackReviewDetailVO(
        AdminTripTrackReviewSummaryVO summary,
        List<AdminTripTrackMemberVO> members,
        List<AdminTripTrackAnomalyVO> anomalies,
        List<AdminTripTrackPointVO> points
) {
}
