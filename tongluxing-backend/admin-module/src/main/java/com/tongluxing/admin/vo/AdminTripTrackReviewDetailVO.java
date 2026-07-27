package com.tongluxing.admin.vo;

import java.util.List;

/** 后台轨迹审核详情，包含汇总、成员、异常和地图轨迹点。 */
public record AdminTripTrackReviewDetailVO(
        AdminTripTrackReviewSummaryVO summary,
        List<AdminTripTrackMemberVO> members,
        List<AdminTripTrackAnomalyVO> anomalies,
        List<AdminTripTrackPointVO> points
) {
}
