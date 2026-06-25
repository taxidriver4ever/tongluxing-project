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

public interface VerificationService {

    VerificationCodeVO createCode(CreateVerificationCodeRequest request);

    VerificationParseVO parse(ParseVerificationRequest request);

    VerificationRecordVO confirm(ConfirmVerificationRequest request);

    PageResult<VerificationRecordVO> pageQuery(VerificationQueryRequest request);

    VerificationRecordVO detail(Long verificationId, Long merchantId);

    VerificationReversalVO applyReversal(Long verificationId, ReversalApplyRequest request);
}
