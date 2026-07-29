package com.tongluxing.merchant.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.merchant.dto.MerchantPartnerApplicationRequest;
import com.tongluxing.merchant.dto.MerchantPartnerCancellationRequest;
import com.tongluxing.merchant.dto.MerchantQueryDTO;
import com.tongluxing.merchant.mapper.MerchantPartnerMapper;
import com.tongluxing.merchant.mapper.MerchantCouponPoolMapper;
import com.tongluxing.merchant.mapper.MerchantProfileMapper;
import com.tongluxing.merchant.service.MerchantPartnerService;
import com.tongluxing.merchant.vo.MerchantPartnerApplicationVO;
import com.tongluxing.merchant.vo.PageResult;
import com.tongluxing.merchant.vo.PartnerCouponPoolVO;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 实现商家合作业务编排，集中处理权限、状态流转和事务边界。
 * 通过 Mapper/外部端口完成持久化或集成，并把内部模型转换为对外视图。
 */
@Service
@RequiredArgsConstructor
public class MerchantPartnerServiceImpl implements MerchantPartnerService {
    private final MerchantPartnerMapper mapper;
    private final MerchantProfileMapper profileMapper;
    private final CurrentUserContext currentUser;
    private final MerchantCouponPoolMapper couponPoolMapper;

    /** 执行 current 对应的领域操作，并返回统一的业务结果。 */
    @Override public MerchantPartnerApplicationVO current() {
        MerchantQueryDTO merchant = requireMerchant();
        return mapper.find(merchant.getMerchantId());
    }

    /** 执行 apply 对应的领域操作，并返回统一的业务结果。 */
    @Override @Transactional public MerchantPartnerApplicationVO apply(MerchantPartnerApplicationRequest request) {
        MerchantQueryDTO merchant = requireMerchant();
        MerchantPartnerApplicationVO old = mapper.find(merchant.getMerchantId());
        if (old != null && List.of("PENDING", "APPROVED").contains(old.applicationStatus())) {
            throw new BusinessException(409, "已有待审核或已通过的合作商申请");
        }
        LocalDateTime now = LocalDateTime.now();
        mapper.submit(merchant.getMerchantId(), request.applicationReason().trim(),
                request.cooperationCategories().trim(), request.plannedMonthlyStock(), now);
        mapper.clearCancellation(merchant.getMerchantId(), now);
        return mapper.find(merchant.getMerchantId());
    }

    @Override @Transactional
    /** 执行 requestCancellation 对应的领域操作，并返回统一的业务结果。 */
    public MerchantPartnerApplicationVO requestCancellation(MerchantPartnerCancellationRequest request) {
        MerchantQueryDTO merchant = requireMerchant();
        MerchantPartnerApplicationVO current = mapper.find(merchant.getMerchantId());
        if (current == null || !"APPROVED".equals(current.applicationStatus())) {
            throw new BusinessException(409, "当前商户不是已生效合作商");
        }
        if ("PENDING".equals(current.cancellationStatus())) {
            throw new BusinessException(409, "取消合作申请正在审核中");
        }
        mapper.requestCancellation(merchant.getMerchantId(), request.reason().trim(), LocalDateTime.now());
        return mapper.find(merchant.getMerchantId());
    }

    /** 执行 applications 对应的领域操作，并返回统一的业务结果。 */
    @Override public PageResult<MerchantPartnerApplicationVO> applications(String status, int page, int size) {
        String normalized = status == null ? "" : status.trim().toUpperCase();
        if (!normalized.isEmpty() && !List.of("PENDING", "APPROVED", "REJECTED", "CANCELLED").contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "合作商审核状态不合法");
        }
        int p = Math.max(1, page), s = Math.min(100, Math.max(1, size));
        return new PageResult<>(mapper.page(normalized, (p - 1) * s, s), mapper.count(normalized), p, s);
    }

    /** 执行 detail 对应的领域操作，并返回统一的业务结果。 */
    @Override public MerchantPartnerApplicationVO detail(Long merchantId) {
        MerchantPartnerApplicationVO value = mapper.find(merchantId);
        if (value == null) throw new BusinessException(ResultCode.NOT_FOUND, "合作商申请不存在");
        return value;
    }

    /** 执行 audit 对应的领域操作，并返回统一的业务结果。 */
    @Override @Transactional public MerchantPartnerApplicationVO audit(Long merchantId, String result, String reason, Long reviewerId) {
        String status = result == null ? "" : result.trim().toUpperCase();
        if (!List.of("APPROVED", "REJECTED").contains(status)) throw new BusinessException("审核结果不合法");
        if ("REJECTED".equals(status) && (reason == null || reason.trim().length() < 2)) throw new BusinessException("拒绝时请填写原因");
        LocalDateTime now = LocalDateTime.now();
        if (mapper.audit(merchantId, status, "REJECTED".equals(status) ? reason.trim() : "", reviewerId, now) != 1) {
            throw new BusinessException(409, "该合作商申请已完成审核");
        }
        if ("APPROVED".equals(status)) mapper.grantPartner(merchantId, now);
        return detail(merchantId);
    }

    @Override @Transactional
    /** 执行 auditCancellation 对应的领域操作，并返回统一的业务结果。 */
    public MerchantPartnerApplicationVO auditCancellation(Long merchantId, String result, String reason, Long reviewerId) {
        String status = result == null ? "" : result.trim().toUpperCase();
        if (!List.of("APPROVED", "REJECTED").contains(status)) {
            throw new BusinessException("取消合作审核结果不合法");
        }
        if ("REJECTED".equals(status) && (reason == null || reason.trim().length() < 2)) {
            throw new BusinessException("拒绝取消合作时请填写原因");
        }
        LocalDateTime now = LocalDateTime.now();
        if (mapper.auditCancellation(merchantId, status,
                "REJECTED".equals(status) ? reason.trim() : "", reviewerId, now) != 1) {
            throw new BusinessException(409, "取消合作申请已完成审核或不存在");
        }
        if ("APPROVED".equals(status)) {
            if (mapper.cancelApplication(merchantId, now) != 1) {
                throw new BusinessException(409, "合作关系状态已发生变化，请刷新后重试");
            }
            mapper.revokePartner(merchantId, now);
            mapper.deactivatePartnerPools(merchantId, now);
            mapper.deactivatePartnerTemplates(merchantId, now);
        }
        return detail(merchantId);
    }

    /** 执行 isApprovedPartner 对应的领域操作，并返回统一的业务结果。 */
    @Override public boolean isApprovedPartner(Long merchantId) {
        MerchantPartnerApplicationVO value = mapper.find(merchantId);
        return value != null && "APPROVED".equals(value.applicationStatus());
    }

    /** 执行 partnerCoupons 对应的领域操作，并返回统一的业务结果。 */
    @Override public PageResult<PartnerCouponPoolVO> partnerCoupons(String status, int page, int size) {
        String normalized=status==null?"":status.trim().toUpperCase();
        if(!normalized.isEmpty()&&!List.of("PENDING","APPROVED","REJECTED").contains(normalized))throw new BusinessException("合作券审核状态不合法");
        int p=Math.max(1,page),s=Math.min(100,Math.max(1,size));
        return new PageResult<>(couponPoolMapper.findPartnerPools(normalized,(p-1)*s,s),couponPoolMapper.countPartnerPools(normalized),p,s);
    }

    /** 执行 partnerCoupon 对应的领域操作，并返回统一的业务结果。 */
    @Override public PartnerCouponPoolVO partnerCoupon(Long id) {
        PartnerCouponPoolVO value=couponPoolMapper.findPartnerPool(id);
        if(value==null)throw new BusinessException(ResultCode.NOT_FOUND,"合作券不存在");
        return value;
    }

    /** 执行 auditPartnerCoupon 对应的领域操作，并返回统一的业务结果。 */
    @Override @Transactional public PartnerCouponPoolVO auditPartnerCoupon(Long id,String result) {
        String status=result==null?"":result.trim().toUpperCase();
        if(!List.of("APPROVED","REJECTED").contains(status))throw new BusinessException("审核结果不合法");
        LocalDateTime now=LocalDateTime.now();
        if(couponPoolMapper.auditPartnerPool(id,status,now)!=1)throw new BusinessException(409,"合作券已完成审核");
        if("APPROVED".equals(status)&&couponPoolMapper.activatePartnerTemplate(id,now)!=1)throw new BusinessException("合作券加入平台券池失败");
        return partnerCoupon(id);
    }

    private MerchantQueryDTO requireMerchant() {
        MerchantQueryDTO value = profileMapper.findByUserId(currentUser.requireUserId());
        if (value == null || !"APPROVED".equals(value.getAuditStatus()) || !"ACTIVE".equals(value.getStatus())) {
            throw new BusinessException(403, "仅已认证普通商户可申请合作商");
        }
        return value;
    }
}
