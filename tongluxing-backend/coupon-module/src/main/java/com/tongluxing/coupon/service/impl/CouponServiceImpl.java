package com.tongluxing.coupon.service.impl;

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
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.coupon.integration.CouponFacade.CouponIssueResult;
import com.tongluxing.coupon.mapper.CouponMapper;
import com.tongluxing.coupon.dto.CouponQueryDTO;
import com.tongluxing.coupon.dto.AdminCouponTemplateVO;
import com.tongluxing.coupon.service.CouponService;
import com.tongluxing.user.model.UserModels.AvailableCouponVO;
import com.tongluxing.user.model.UserModels.CouponCountVO;
import com.tongluxing.user.model.UserModels.CouponDeductionVO;
import com.tongluxing.user.model.UserModels.CouponLockRequest;
import com.tongluxing.user.model.UserModels.CouponOrderResultRequest;
import com.tongluxing.user.model.UserModels.CouponSummaryVO;
import com.tongluxing.user.model.UserModels.PageResult;
import com.tongluxing.user.model.UserModels.UserCouponDetailVO;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 优惠券模块业务实现。
 *
 * <p>负责用户优惠券查询、可用券筛选、领取/发券幂等、锁券以及订单支付结果后的状态流转。</p>
 */
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {
    private final CouponMapper mapper;
    private final CurrentUserContext currentUser;
    private final ObjectMapper objectMapper;

    /**
     * 分页查询当前登录用户的优惠券列表。
     *
     * <p>分页参数在服务层统一兜底，避免异常参数造成过大查询。</p>
     */
    @Override
    public PageResult<CouponSummaryVO> currentCoupons(String status, String type, int page, int size) {
        long userId = currentUser.requireUserId();
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(100, Math.max(1, size));
        List<CouponSummaryVO> records = mapper.findCoupons(userId, status, type,
                (normalizedPage - 1) * normalizedSize, normalizedSize).stream().map(this::summary).toList();
        return new PageResult<>(records, mapper.countCoupons(userId, status, type), normalizedPage, normalizedSize);
    }

    /**
     * 查询当前登录用户的优惠券详情。
     */
    @Override
    public UserCouponDetailVO currentCoupon(Long id) {
        CouponQueryDTO row = mapper.findCoupon(id, currentUser.requireUserId());
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "优惠券不存在");
        }
        return detail(row);
    }

    /**
     * 查询当前订单可用优惠券。
     *
     * <p>数据库先筛选状态、有效期、金额门槛和商户，再由 scopeAllows 解析 JSON 范围规则。</p>
     */
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

    /**
     * 当前用户主动领取优惠券模板。
     *
     * <p>领取场景使用固定的 sourceType/sourceBizId，保证重复点击不会重复发券。</p>
     */
    @Override
    public CouponIssueResult claim(Long templateId) {
        long userId = currentUser.requireUserId();
        return issue(userId, templateId, "CLAIM", "CLAIM:" + userId + ":" + templateId);
    }

    @Override
    public List<AdminCouponTemplateVO> claimableTemplates() {
        currentUser.requireUserId();
        return mapper.findClaimableTemplates();
    }

    /**
     * 向用户发放优惠券。
     *
     * <p>完整流程：先查幂等记录 → 校验模板 → 计算有效期 → 扣减库存 → 写入用户优惠券。
     * 如果并发请求触发唯一键冲突，会重新查询已有记录并返回 duplicate=true。</p>
     */
    @Override
    @Transactional
    public CouponIssueResult issue(Long userId, Long templateId, String sourceType, String sourceBizId) {
        CouponQueryDTO existing = mapper.findBySource(userId, templateId, sourceType, sourceBizId);
        if (existing != null) {
            return new CouponIssueResult(existing.getId(), existing.getCouponStatus(), true);
        }
        CouponQueryDTO template = mapper.findTemplate(templateId);
        if (template == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "优惠券模板不存在或未启用");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = template.getValidStartAt() == null ? now : template.getValidStartAt();
        LocalDateTime end = template.getValidEndAt();
        if ("DAYS_AFTER_CLAIM".equals(template.getValidityType())) {
            start = now;
            end = now.plusDays(template.getValidDays() == null ? 1 : template.getValidDays());
        }
        if (end == null || !end.isAfter(now)) {
            throw new BusinessException(409, "优惠券模板已过期");
        }
        long id = SnowflakeIdGenerator.nextId();
        try {
            mapper.insertCoupon(id, userId, templateId, sourceType, sourceBizId, start, end, now);
        } catch (DuplicateKeyException e) {
            existing = mapper.findBySourceForUpdate(userId, templateId, sourceType, sourceBizId);
            return new CouponIssueResult(existing.getId(), existing.getCouponStatus(), true);
        }
        if (mapper.increaseClaimed(templateId, now) == 0) {
            throw new BusinessException(409, "优惠券已领完");
        }
        if (mapper.isMerchantOfferTemplate(templateId) > 0
                && mapper.consumeMerchantOfferStock(templateId, now) == 0) {
            throw new BusinessException(409, "商家优惠券库存不足或未到领取时间");
        }
        if (mapper.isPartnerPoolTemplate(templateId) > 0
                && mapper.consumePartnerPoolStock(templateId, now) == 0) {
            throw new BusinessException(409, "合作券库存不足或已下架");
        }
        return new CouponIssueResult(id, "AVAILABLE", false);
    }

    /**
     * 锁定优惠券用于订单结算。
     *
     * <p>锁定成功后返回本次可抵扣金额；若状态、有效期或门槛不满足，则抛出冲突异常。</p>
     */
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

    /**
     * 处理订单支付结果。
     *
     * <p>支付成功时将锁定券确认为 USED；支付失败、取消等场景释放为 AVAILABLE。</p>
     */
    @Override
    @Transactional
    public void orderResult(CouponOrderResultRequest request) {
        int changed = "SUCCESS".equals(request.payStatus())
                ? mapper.confirm(request.orderId(), LocalDateTime.now())
                : mapper.release(request.orderId(), LocalDateTime.now());
        if (changed > 0) {
            return;
        }
        // 订单结果回调来自补偿任务，必须允许重复投递幂等成功。
        if ("SUCCESS".equals(request.payStatus()) && mapper.countUsedByOrder(request.orderId()) > 0) {
            return;
        }
        if (!"SUCCESS".equals(request.payStatus()) && mapper.countLockedByOrder(request.orderId()) == 0) {
            return;
        }
        throw new BusinessException(409, "订单优惠券状态冲突");
    }

    /**
     * 统计指定用户可用券和即将过期券数量。
     */
    @Override
    public CouponCountVO count(Long userId) {
        return new CouponCountVO(mapper.countAvailable(userId), mapper.countExpiring(userId));
    }

    /**
     * 判断优惠券适用范围是否覆盖当前订单场景。
     *
     * <p>scope_json 为空表示不限制；解析失败时按不可用处理，避免错误配置被误用。</p>
     */
    private boolean scopeAllows(String json, String orderType, Long merchantId) {
        if (!StringUtils.hasText(json)) {
            return true;
        }
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

    /**
     * 将查询结果转换成优惠券列表展示 VO。
     */
    private CouponSummaryVO summary(CouponQueryDTO row) {
        return new CouponSummaryVO(row.getId(), row.getTemplateId(), row.getCouponName(), row.getCouponType(),
                row.getThresholdAmount(), row.getDiscountAmount(), row.getCouponStatus(),
                row.getValidStartAt(), row.getValidEndAt());
    }

    /**
     * 将查询结果转换成优惠券详情 VO。
     */
    private UserCouponDetailVO detail(CouponQueryDTO row) {
        return new UserCouponDetailVO(row.getId(), row.getTemplateId(), row.getCouponName(), row.getCouponType(),
                row.getIssuerId(), row.getThresholdAmount(), row.getDiscountAmount(), row.getScopeJson(),
                row.getCouponStatus(), row.getValidStartAt(), row.getValidEndAt(),
                row.getLockedOrderId(), row.getUsedOrderId());
    }
}
