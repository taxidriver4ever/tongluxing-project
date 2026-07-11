package com.tongluxing.assessment.service;

import com.tongluxing.assessment.dto.ManualAssessmentAdjustmentRequest;
import com.tongluxing.assessment.dto.MonthlyAssessmentRunRequest;
import com.tongluxing.assessment.dto.RecalculateMerchantAssessmentRequest;
import com.tongluxing.assessment.vo.MerchantAssessmentResultVO;
import com.tongluxing.assessment.vo.MerchantAssessmentSnapshotVO;
import com.tongluxing.assessment.vo.MonthlyRunResultVO;

/**
 * 商家考核业务服务。
 *
 * <p>该接口是考核模块的统一业务入口，外部调用方不应直接拼装考核 SQL 或自行刷新商家等级字段。
 * 这样可以保证“分数明细、商家快照、缓存”三处数据始终按同一套规则变更。</p>
 */
public interface AssessmentService {

    /**
     * 查询当前登录用户对应商家的最新考核结果。
     *
     * @return 最新考核结果和距离下一等级所需分数
     */
    MerchantAssessmentResultVO currentMerchantAssessment();

    /**
     * 重新计算指定商家在某个月份的考核结果。
     *
     * @param merchantId 商家 ID
     * @param request 重算周期、操作人、原因和幂等请求号
     * @return 重算后的考核结果
     */
    MerchantAssessmentResultVO recalculate(Long merchantId, RecalculateMerchantAssessmentRequest request);

    /**
     * 批量执行指定月份的月度考核。
     *
     * @param request 月度考核批处理请求
     * @return 本次扫描商家数量和成功数量
     */
    MonthlyRunResultVO runMonthly(MonthlyAssessmentRunRequest request);

    /**
     * 读取商家等级快照，优先返回缓存，缓存未命中时回源商家表。
     *
     * @param merchantId 商家 ID
     * @return 商家等级、佣金率、排序权重等轻量快照
     */
    MerchantAssessmentSnapshotVO snapshot(Long merchantId);

    /**
     * 人工调整商家考核分，调整记录会单独落表用于审计。
     *
     * @param merchantId 商家 ID
     * @param request 调整分值、原因、操作人和幂等请求号
     * @return 调整后的最新考核结果
     */
    MerchantAssessmentResultVO manualAdjust(Long merchantId, ManualAssessmentAdjustmentRequest request);
}
