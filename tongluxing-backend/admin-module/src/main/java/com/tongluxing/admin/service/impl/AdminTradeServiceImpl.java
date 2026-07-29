package com.tongluxing.admin.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tongluxing.admin.dto.AdminAuditRequest;
import com.tongluxing.admin.entity.AdminAuditLog;
import com.tongluxing.admin.mapper.AdminAuditLogMapper;
import com.tongluxing.admin.mapper.AdminTradeMapper;
import com.tongluxing.admin.service.AdminTradeService;
import com.tongluxing.admin.vo.*;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.user.support.CurrentUserContext;
import lombok.RequiredArgsConstructor;

/**
 * 实现管理端交易业务编排，集中处理权限、状态流转和事务边界。
 * 通过 Mapper/外部端口完成持久化或集成，并把内部模型转换为对外视图。
 */
@Service @RequiredArgsConstructor
public class AdminTradeServiceImpl implements AdminTradeService {
    private final AdminTradeMapper mapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final AdminSupport support;
    private final CurrentUserContext currentUser;

    /** 执行 orders 对应的领域操作，并返回统一的业务结果。 */
    @Override public PageResult<AdminOrderVO> orders(String status,String keyword,LocalDateTime start,LocalDateTime end,int page,int size){
        page=Math.max(1,page);size=Math.min(100,Math.max(1,size));int offset=(page-1)*size;
        return new PageResult<>(mapper.pageOrders(status,keyword,start,end,offset,size),mapper.countOrders(status,keyword,start,end),page,size);
    }
    /** 执行 order 对应的领域操作，并返回统一的业务结果。 */
    @Override public AdminOrderVO order(Long id){AdminOrderVO v=mapper.findOrder(id);if(v==null)throw new BusinessException(404,"订单不存在");return v;}
    /** 执行 refunds 对应的领域操作，并返回统一的业务结果。 */
    @Override public PageResult<AdminRefundVO> refunds(String status,int page,int size){page=Math.max(1,page);size=Math.min(100,Math.max(1,size));return new PageResult<>(mapper.pageRefunds(status,(page-1)*size,size),mapper.countRefunds(status),page,size);}
    /** 执行 refund 对应的领域操作，并返回统一的业务结果。 */
    @Override public AdminRefundVO refund(Long id){AdminRefundVO v=mapper.findRefund(id);if(v==null)throw new BusinessException(404,"退款单不存在");return v;}

    @Override @Transactional
    /** 执行 auditRefund 对应的领域操作，并返回统一的业务结果。 */
    public AdminAuditResultVO auditRefund(Long id,AdminAuditRequest request){
        AdminAuditLog repeated=auditLogMapper.findByRequestId(request.requestId());
        if(repeated!=null)return repeated(repeated,request.auditResult());
        String result=normalize(request.auditResult());String reason=reason(result,request.rejectReason());
        AdminRefundVO before=refund(id);if(!"PENDING".equals(before.auditStatus()))throw new BusinessException(409,"退款申请已办理");
        LocalDateTime now=LocalDateTime.now();boolean approved="APPROVED".equals(result);
        int changed=mapper.auditRefund(id,result,approved?"SUCCESS":"REJECTED",approved?now:null,now);
        if(changed!=1)throw new BusinessException(409,"退款状态已变更");
        mapper.updateOrderRefund(before.orderId(),approved?"SUCCESS":"NONE",approved?"REFUNDED":"PAID",now);
        AdminAuditLog log=support.audit("REFUND_AUDIT","payment-module","REFUND",String.valueOf(id),request.requestId(),currentUser.requireUserId(),reason,"SUCCESS",
                "{\"auditStatus\":\"PENDING\"}","{\"auditStatus\":\"%s\",\"refundStatus\":\"%s\"}".formatted(result,approved?"SUCCESS":"REJECTED"));
        return completed(log,result,reason,now);
    }

    /** 执行 settlements 对应的领域操作，并返回统一的业务结果。 */
    @Override public PageResult<AdminSettlementVO> settlements(String status,int page,int size){page=Math.max(1,page);size=Math.min(100,Math.max(1,size));return new PageResult<>(mapper.pageSettlements(status,(page-1)*size,size),mapper.countSettlements(status),page,size);}
    @Override @Transactional
    /** 执行 triggerSettlement 对应的领域操作，并返回统一的业务结果。 */
    public AdminAuditResultVO triggerSettlement(Long id,AdminAuditRequest request){
        AdminAuditLog repeated=auditLogMapper.findByRequestId(request.requestId());
        if(repeated!=null)return repeated(repeated,request.auditResult());
        if(!"APPROVED".equals(normalize(request.auditResult())))throw new BusinessException(400,"触发结算只支持 APPROVED");
        Long orderId=mapper.settlementOrderId(id);if(orderId==null)throw new BusinessException(404,"结算单不存在");
        LocalDateTime now=LocalDateTime.now();if(mapper.triggerSettlement(id,now)!=1)throw new BusinessException(409,"结算单已处理或状态不允许");
        mapper.completeOrder(orderId,now);
        AdminAuditLog log=support.audit("SETTLEMENT_TRIGGER","payment-module","SETTLEMENT",String.valueOf(id),request.requestId(),currentUser.requireUserId(),request.rejectReason(),"SUCCESS","{\"status\":\"WAIT_SHARING\"}","{\"status\":\"SUCCESS\"}");
        return completed(log,"APPROVED",null,now);
    }
    /** 执行 verifications 对应的领域操作，并返回统一的业务结果。 */
    @Override public PageResult<AdminVerificationRecordVO> verifications(String status,int page,int size){page=Math.max(1,page);size=Math.min(100,Math.max(1,size));return new PageResult<>(mapper.pageVerifications(status,(page-1)*size,size),mapper.countVerifications(status),page,size);}
    /** 执行 transactions 对应的领域操作，并返回统一的业务结果。 */
    @Override public PageResult<AdminTransactionVO> transactions(String type,int page,int size){page=Math.max(1,page);size=Math.min(100,Math.max(1,size));List<AdminTransactionVO> rows=mapper.pageTransactions(type,(page-1)*size,size);return new PageResult<>(rows,mapper.countTransactions(),page,size);}

    private String normalize(String value){String v=value==null?"":value.trim().toUpperCase();if(!List.of("APPROVED","REJECTED").contains(v))throw new BusinessException(400,"审核结果仅支持 APPROVED 或 REJECTED");return v;}
    private String reason(String result,String value){String v=value==null?"":value.trim();if("REJECTED".equals(result)&&v.length()<2)throw new BusinessException(400,"拒绝时必须填写原因");return "APPROVED".equals(result)?null:v;}
    private AdminAuditResultVO completed(AdminAuditLog l,String result,String reason,LocalDateTime now){return new AdminAuditResultVO(l.getId(),l.getTargetModule(),l.getTargetType(),l.getTargetId(),result,"SUCCESS","SUCCESS",reason,l.getOperatorId(),"处理完成",l.getCreatedAt(),now);}
    private AdminAuditResultVO repeated(AdminAuditLog l,String result){return new AdminAuditResultVO(l.getId(),l.getTargetModule(),l.getTargetType(),l.getTargetId(),result,"SUCCESS","SUCCESS",l.getOperationReason(),l.getOperatorId(),"重复请求，返回已有结果",l.getCreatedAt(),l.getCreatedAt());}
}
