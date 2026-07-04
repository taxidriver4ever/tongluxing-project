package com.tongluxing.verification.service;

import com.tongluxing.verification.dto.ConfirmVerificationRequest;
import com.tongluxing.verification.dto.CreateVerificationCodeRequest;
import com.tongluxing.verification.dto.ParseVerificationRequest;
import com.tongluxing.verification.dto.ReversalApplyRequest;
import com.tongluxing.verification.dto.VerificationQueryRequest;
import com.tongluxing.verification.vo.PageResult;
import com.tongluxing.verification.vo.VerificationCodeVO;
import com.tongluxing.verification.vo.VerificationParseVO;
import com.tongluxing.verification.vo.VerificationRecordVO;
import com.tongluxing.verification.vo.VerificationReversalVO;

/**
 * 券核销模块业务服务接口。
 */
public interface VerificationService {

    /**
     * 创建核销码。
     */
    VerificationCodeVO createCode(CreateVerificationCodeRequest request);

    /**
     * 解析核销码。
     */
    VerificationParseVO parse(ParseVerificationRequest request);

    /**
     * 确认核销。
     */
    VerificationRecordVO confirm(ConfirmVerificationRequest request);

    /**
     * 分页查询核销记录。
     */
    PageResult<VerificationRecordVO> pageQuery(VerificationQueryRequest request);

    /**
     * 查询核销记录详情。
     */
    VerificationRecordVO detail(Long verificationId, Long merchantId);

    /**
     * 申请核销冲正。
     */
    VerificationReversalVO applyReversal(Long verificationId, ReversalApplyRequest request);
}
