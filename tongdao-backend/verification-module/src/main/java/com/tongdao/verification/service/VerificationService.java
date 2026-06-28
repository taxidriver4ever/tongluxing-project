package com.tongdao.verification.service;

import com.tongdao.verification.dto.ConfirmVerificationRequest;
import com.tongdao.verification.dto.CreateVerificationCodeRequest;
import com.tongdao.verification.dto.ParseVerificationRequest;
import com.tongdao.verification.dto.ReversalApplyRequest;
import com.tongdao.verification.dto.VerificationQueryRequest;
import com.tongdao.verification.vo.PageResult;
import com.tongdao.verification.vo.VerificationCodeVO;
import com.tongdao.verification.vo.VerificationParseVO;
import com.tongdao.verification.vo.VerificationRecordVO;
import com.tongdao.verification.vo.VerificationReversalVO;

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
