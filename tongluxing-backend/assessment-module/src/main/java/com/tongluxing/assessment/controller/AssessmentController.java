package com.tongluxing.assessment.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.assessment.dto.ManualAssessmentAdjustmentRequest;
import com.tongluxing.assessment.dto.MonthlyAssessmentRunRequest;
import com.tongluxing.assessment.dto.RecalculateMerchantAssessmentRequest;
import com.tongluxing.assessment.service.AssessmentService;
import com.tongluxing.assessment.vo.MerchantAssessmentResultVO;
import com.tongluxing.assessment.vo.MerchantAssessmentSnapshotVO;
import com.tongluxing.assessment.vo.MonthlyRunResultVO;
import com.tongluxing.common.result.Result;

import lombok.RequiredArgsConstructor;

/**
 * 商家考核接口。
 */
@RestController
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    @GetMapping("/v1/assessments/merchants/me")
    public Result<MerchantAssessmentResultVO> currentMerchantAssessment() {
        return Result.success(assessmentService.currentMerchantAssessment());
    }

    @PostMapping("/internal/v1/assessments/merchants/{merchantId}/recalculate")
    public Result<MerchantAssessmentResultVO> recalculate(
            @PathVariable Long merchantId,
            @Valid @RequestBody RecalculateMerchantAssessmentRequest request) {
        return Result.success(assessmentService.recalculate(merchantId, request));
    }

    @PostMapping("/internal/v1/assessments/monthly/run")
    public Result<MonthlyRunResultVO> runMonthly(@Valid @RequestBody MonthlyAssessmentRunRequest request) {
        return Result.success(assessmentService.runMonthly(request));
    }

    @GetMapping("/internal/v1/assessments/merchants/{merchantId}/snapshot")
    public Result<MerchantAssessmentSnapshotVO> snapshot(@PathVariable Long merchantId) {
        return Result.success(assessmentService.snapshot(merchantId));
    }

    /**
     * 运营人工调整考核分，记录调整原因和操作人。
     */
    @PostMapping("/v1/admin/assessments/merchants/{merchantId}/adjustments")
    public Result<MerchantAssessmentResultVO> manualAdjust(
            @PathVariable Long merchantId,
            @Valid @RequestBody ManualAssessmentAdjustmentRequest request) {
        return Result.success(assessmentService.manualAdjust(merchantId, request));
    }
}
