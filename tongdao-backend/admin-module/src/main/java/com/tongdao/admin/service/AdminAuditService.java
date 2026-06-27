package com.tongdao.admin.service;

import com.tongdao.admin.dto.AdminAuditRequest;
import com.tongdao.admin.vo.AdminAuditResultVO;

/**
 * 运营后台审核服务。
 *
 * <p>统一承接用户认证、商家入驻、退款、核销撤销和结算触发等后台审核动作。</p>
 */
public interface AdminAuditService {

    /** 审核用户认证申请。 */
    AdminAuditResultVO auditUserCertification(Long certificationId, AdminAuditRequest request);

    /** 审核商家入驻申请。 */
    AdminAuditResultVO auditMerchantApplication(Long applicationId, AdminAuditRequest request);

    /** 审核退款申请。 */
    AdminAuditResultVO auditRefund(Long refundId, AdminAuditRequest request);

    /** 审核核销撤销申请。 */
    AdminAuditResultVO auditVerificationReversal(Long reversalId, AdminAuditRequest request);

    /** 触发结算处理。 */
    AdminAuditResultVO triggerSettlement(Long settlementId, AdminAuditRequest request);
}
