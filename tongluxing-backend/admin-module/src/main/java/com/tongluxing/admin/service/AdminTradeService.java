package com.tongluxing.admin.service;

import java.time.LocalDateTime;
import com.tongluxing.admin.dto.AdminAuditRequest;
import com.tongluxing.admin.vo.*;

/** 运营后台交易查询与可审计人工动作。 */
public interface AdminTradeService {
    PageResult<AdminOrderVO> orders(String status,String keyword,LocalDateTime startTime,LocalDateTime endTime,int page,int size);
    AdminOrderVO order(Long id);
    PageResult<AdminRefundVO> refunds(String status,int page,int size);
    AdminRefundVO refund(Long id);
    AdminAuditResultVO auditRefund(Long id,AdminAuditRequest request);
    PageResult<AdminSettlementVO> settlements(String status,int page,int size);
    AdminAuditResultVO triggerSettlement(Long id,AdminAuditRequest request);
    PageResult<AdminVerificationRecordVO> verifications(String status,int page,int size);
    PageResult<AdminTransactionVO> transactions(String type,int page,int size);
}
