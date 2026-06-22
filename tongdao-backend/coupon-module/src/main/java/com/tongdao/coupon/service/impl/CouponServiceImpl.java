package com.tongdao.coupon.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.coupon.integration.CouponFacade.CouponIssueResult;
import com.tongdao.coupon.mapper.CouponMapper;
import com.tongdao.coupon.dto.CouponQueryDTO;
import com.tongdao.coupon.service.CouponService;
import com.tongdao.user.model.UserModels.AvailableCouponVO;
import com.tongdao.user.model.UserModels.CouponCountVO;
import com.tongdao.user.model.UserModels.CouponDeductionVO;
import com.tongdao.user.model.UserModels.CouponLockRequest;
import com.tongdao.user.model.UserModels.CouponOrderResultRequest;
import com.tongdao.user.model.UserModels.CouponSummaryVO;
import com.tongdao.user.model.UserModels.PageResult;
import com.tongdao.user.model.UserModels.UserCouponDetailVO;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {
    private final CouponMapper mapper;
    private final CurrentUserContext currentUser;
    private final ObjectMapper objectMapper;

    @Override
    public PageResult<CouponSummaryVO> currentCoupons(String status, String type, int page, int size) {
        long userId = currentUser.requireUserId();
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(100, Math.max(1, size));
        List<CouponSummaryVO> records = mapper.findCoupons(userId, status, type,
                (normalizedPage - 1) * normalizedSize, normalizedSize).stream().map(this::summary).toList();
        return new PageResult<>(records, mapper.countCoupons(userId, status, type), normalizedPage, normalizedSize);
    }

    @Override
    public UserCouponDetailVO currentCoupon(Long id) {
        CouponQueryDTO row = mapper.findCoupon(id, currentUser.requireUserId());
        if (row == null) throw new BusinessException(ResultCode.NOT_FOUND, "优惠券不存在");
        return detail(row);
    }

    @Override
    public List<AvailableCouponVO> available(String orderType, Long merchantId, BigDecimal amount) {
        if (!StringUtils.hasText(orderType) || amount == null || amount.signum() < 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "订单参数不完整");
        }
        return mapper.findAvailable(currentUser.requireUserId(), merchantId, amount).stream()
                .filter(row -> scopeAllows(row.getScopeJson(), orderType, merchantId))
                .map(row -> new AvailableCouponVO(row.getId(), row.getCouponName(),
                        row.getDeductionAmount(), row.getValidEndAt()))
                .toList();
    }

    @Override
    public CouponIssueResult claim(Long templateId) {
        long userId = currentUser.requireUserId();
        return issue(userId, templateId, "CLAIM", "CLAIM:" + userId + ":" + templateId);
    }

    @Override
    @Transactional
    public CouponIssueResult issue(Long userId, Long templateId, String sourceType, String sourceBizId) {
        CouponQueryDTO existing = mapper.findBySource(userId, templateId, sourceType, sourceBizId);
        if (existing != null) return new CouponIssueResult(existing.getId(), existing.getCouponStatus(), true);
        CouponQueryDTO template = mapper.findTemplate(templateId);
        if (template == null) throw new BusinessException(ResultCode.NOT_FOUND, "优惠券模板不存在或未启用");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = template.getValidStartAt() == null ? now : template.getValidStartAt();
        LocalDateTime end = template.getValidEndAt();
        if ("DAYS_AFTER_CLAIM".equals(template.getValidityType())) {
            start = now;
            end = now.plusDays(template.getValidDays() == null ? 1 : template.getValidDays());
        }
        if (end == null || !end.isAfter(now)) throw new BusinessException(409, "优惠券模板已过期");
        if (mapper.increaseClaimed(templateId, now) == 0) throw new BusinessException(409, "优惠券已领完");
        long id = SnowflakeIdGenerator.nextId();
        try {
            mapper.insertCoupon(id, userId, templateId, sourceType, sourceBizId, start, end, now);
        } catch (DuplicateKeyException e) {
            existing = mapper.findBySource(userId, templateId, sourceType, sourceBizId);
            return new CouponIssueResult(existing.getId(), existing.getCouponStatus(), true);
        }
        return new CouponIssueResult(id, "AVAILABLE", false);
    }

    @Override
    @Transactional
    public CouponDeductionVO lock(Long id, CouponLockRequest request) {
        LocalDateTime now = LocalDateTime.now();
        if (mapper.lock(id, request.orderId(), request.amount(), now) == 0) {
            throw new BusinessException(409, "优惠券状态冲突或不满足使用规则");
        }
        CouponQueryDTO row = mapper.findLocked(id, request.orderId());
        return new CouponDeductionVO(id, request.orderId(), row.getDiscountAmount(), "LOCKED");
    }

    @Override
    @Transactional
    public void orderResult(CouponOrderResultRequest request) {
        int changed = "SUCCESS".equals(request.payStatus())
                ? mapper.confirm(request.orderId(), LocalDateTime.now())
                : mapper.release(request.orderId(), LocalDateTime.now());
        if (changed == 0) throw new BusinessException(409, "订单优惠券状态冲突");
    }

    @Override
    public CouponCountVO count(Long userId) {
        return new CouponCountVO(mapper.countAvailable(userId), mapper.countExpiring(userId));
    }

    private boolean scopeAllows(String json, String orderType, Long merchantId) {
        if (!StringUtils.hasText(json)) return true;
        try {
            Map<String, Object> scope = objectMapper.readValue(json, new TypeReference<>() { });
            Object orderTypes = scope.get("orderTypes");
            Object merchantIds = scope.get("merchantIds");
            boolean orderAllowed = !(orderTypes instanceof List<?> values) || values.isEmpty() || values.contains(orderType);
            boolean merchantAllowed = !(merchantIds instanceof List<?> values) || values.isEmpty()
                    || merchantId != null && values.stream().anyMatch(v -> merchantId.toString().equals(v.toString()));
            return orderAllowed && merchantAllowed;
        } catch (Exception e) {
            return false;
        }
    }

    private CouponSummaryVO summary(CouponQueryDTO row) {
        return new CouponSummaryVO(row.getId(), row.getTemplateId(), row.getCouponName(), row.getCouponType(),
                row.getThresholdAmount(), row.getDiscountAmount(), row.getCouponStatus(),
                row.getValidStartAt(), row.getValidEndAt());
    }

    private UserCouponDetailVO detail(CouponQueryDTO row) {
        return new UserCouponDetailVO(row.getId(), row.getTemplateId(), row.getCouponName(), row.getCouponType(),
                row.getIssuerId(), row.getThresholdAmount(), row.getDiscountAmount(), row.getScopeJson(),
                row.getCouponStatus(), row.getValidStartAt(), row.getValidEndAt(),
                row.getLockedOrderId(), row.getUsedOrderId());
    }
}
