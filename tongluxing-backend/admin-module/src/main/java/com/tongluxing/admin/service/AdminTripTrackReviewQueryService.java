package com.tongluxing.admin.service;

import org.springframework.stereotype.Service;

import com.tongluxing.admin.mapper.AdminTripTrackReviewMapper;
import com.tongluxing.admin.vo.AdminTripTrackReviewDetailVO;
import com.tongluxing.admin.vo.AdminTripTrackReviewSummaryVO;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;

import lombok.RequiredArgsConstructor;

/** 后台轨迹异常列表与详情查询服务。 */
@Service
@RequiredArgsConstructor
public class AdminTripTrackReviewQueryService {

    private final AdminTripTrackReviewMapper mapper;

    public PageResult<AdminTripTrackReviewSummaryVO> page(
            String riskLevel, String status, int page, int size) {
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(100, Math.max(1, size));
        String normalizedRisk = normalize(riskLevel);
        String normalizedStatus = normalize(status);
        return new PageResult<>(
                mapper.page(normalizedRisk, normalizedStatus,
                        (normalizedPage - 1) * normalizedSize, normalizedSize),
                mapper.count(normalizedRisk, normalizedStatus),
                normalizedPage, normalizedSize);
    }

    public AdminTripTrackReviewDetailVO detail(Long tripId) {
        AdminTripTrackReviewSummaryVO summary = mapper.detail(tripId);
        if (summary == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "轨迹审核记录不存在");
        }
        return new AdminTripTrackReviewDetailVO(
                summary, mapper.members(tripId), mapper.anomalies(tripId), mapper.points(tripId));
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
