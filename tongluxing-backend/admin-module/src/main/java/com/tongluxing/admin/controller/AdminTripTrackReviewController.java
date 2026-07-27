package com.tongluxing.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.service.AdminTripTrackReviewQueryService;
import com.tongluxing.admin.vo.AdminTripTrackReviewDetailVO;
import com.tongluxing.admin.vo.AdminTripTrackReviewSummaryVO;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.common.result.Result;
import com.tongluxing.trip.dto.TripTrackReviewRequest;
import com.tongluxing.trip.service.TripSettlementService;
import com.tongluxing.trip.vo.TripTrackReviewResponse;
import com.tongluxing.user.support.CurrentUserContext;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** 管理员轨迹异常与成长值人工审核接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/trip-track-reviews")
public class AdminTripTrackReviewController {

    private final TripSettlementService tripSettlementService;
    private final AdminTripTrackReviewQueryService queryService;
    private final CurrentUserContext currentUserContext;

    /** 分页查询待审核和历史轨迹风险记录。 */
    @GetMapping
    public Result<PageResult<AdminTripTrackReviewSummaryVO>> page(
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String settlementStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(queryService.page(riskLevel, settlementStatus, page, size));
    }

    /** 查询原始/过滤/审核里程、成员轨迹、异常前后点和地图轨迹。 */
    @GetMapping("/{tripId}")
    public Result<AdminTripTrackReviewDetailVO> detail(@PathVariable Long tripId) {
        return Result.success(queryService.detail(tripId));
    }

    /**
     * 审核轨迹结算。原始轨迹不可修改，只能通过系统里程、修正审核后里程、驳回或标记误报。
     */
    @PostMapping("/{tripId}")
    public Result<TripTrackReviewResponse> review(
            @PathVariable Long tripId,
            @Valid @RequestBody TripTrackReviewRequest request) {
        return Result.success(tripSettlementService.reviewTrack(
                tripId, currentUserContext.requireUserId(), request));
    }
}
