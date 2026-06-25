package com.tongdao.admin.service;

import com.tongdao.admin.dto.AdminAuditRequest;
import com.tongdao.admin.vo.AdminAuditResultVO;

public interface AdminAuditService {

    AdminAuditResultVO auditUserCertification(Long certificationId, AdminAuditRequest request);

    AdminAuditResultVO auditMerchantApplication(Long applicationId, AdminAuditRequest request);

    AdminAuditResultVO auditRefund(Long refundId, AdminAuditRequest request);

    AdminAuditResultVO auditVerificationReversal(Long reversalId, AdminAuditRequest request);

    AdminAuditResultVO triggerSettlement(Long settlementId, AdminAuditRequest request);
}
