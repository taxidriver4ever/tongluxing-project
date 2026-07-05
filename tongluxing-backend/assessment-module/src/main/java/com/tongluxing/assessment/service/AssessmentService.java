package com.tongluxing.assessment.service;

import com.tongluxing.assessment.dto.ManualAssessmentAdjustmentRequest;
import com.tongluxing.assessment.dto.MonthlyAssessmentRunRequest;
import com.tongluxing.assessment.dto.RecalculateMerchantAssessmentRequest;
import com.tongluxing.assessment.vo.MerchantAssessmentResultVO;
import com.tongluxing.assessment.vo.MerchantAssessmentSnapshotVO;
import com.tongluxing.assessment.vo.MonthlyRunResultVO;

/**
 * 商家考核业务服务。
 */
public interface AssessmentService {

    MerchantAssessmentResultVO currentMerchantAssessment();

    MerchantAssessmentResultVO recalculate(Long merchantId, RecalculateMerchantAssessmentRequest request);

    MonthlyRunResultVO runMonthly(MonthlyAssessmentRunRequest request);

    MerchantAssessmentSnapshotVO snapshot(Long merchantId);

    MerchantAssessmentResultVO manualAdjust(Long merchantId, ManualAssessmentAdjustmentRequest request);
}
