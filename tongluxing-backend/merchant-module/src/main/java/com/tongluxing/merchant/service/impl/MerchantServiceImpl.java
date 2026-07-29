package com.tongluxing.merchant.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.event.UserRegisteredEvent;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.merchant.dto.CreateMerchantCouponPoolRequest;
import com.tongluxing.merchant.dto.CreateMerchantProductRequest;
import com.tongluxing.merchant.dto.CreatePromotionCodeRequest;
import com.tongluxing.merchant.dto.MerchantApplicationRequest;
import com.tongluxing.merchant.dto.MerchantQueryDTO;
import com.tongluxing.merchant.dto.MerchantSettlementRequest;
import com.tongluxing.merchant.dto.UpdateMerchantProductRequest;
import com.tongluxing.merchant.dto.UpdateMerchantProfileRequest;
import com.tongluxing.merchant.dto.UpdateRewardPoolRequest;
import com.tongluxing.merchant.mapper.MerchantAuditLogMapper;
import com.tongluxing.merchant.mapper.MerchantCouponPoolMapper;
import com.tongluxing.merchant.mapper.MerchantProductMapper;
import com.tongluxing.merchant.mapper.MerchantProfileMapper;
import com.tongluxing.merchant.mapper.MerchantPromotionCodeMapper;
import com.tongluxing.merchant.mapper.MerchantPromotionStatsMapper;
import com.tongluxing.merchant.mapper.MerchantRewardPoolConfigMapper;
import com.tongluxing.merchant.mapper.MerchantUserRelationMapper;
import com.tongluxing.merchant.service.MerchantService;
import com.tongluxing.merchant.service.MerchantPartnerService;
import com.tongluxing.merchant.vo.MerchantAssessmentVO;
import com.tongluxing.merchant.vo.MerchantCouponPoolVO;
import com.tongluxing.merchant.vo.MerchantProductVO;
import com.tongluxing.merchant.vo.MerchantProfileVO;
import com.tongluxing.merchant.vo.MerchantPromotionCodeVO;
import com.tongluxing.merchant.vo.MerchantPromotionStatsVO;
import com.tongluxing.merchant.vo.MerchantRewardPoolVO;
import com.tongluxing.merchant.vo.PageResult;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 商家模块业务实现。
 *
 * <p>覆盖商家入驻、资料、商品、券池、奖励池、推广码、统计和考核展示。
 * Redis 只作为缓存/幂等辅助，MySQL 仍是事实来源。</p>
 */
@Service
@RequiredArgsConstructor
public class MerchantServiceImpl implements MerchantService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String AUDIT_PENDING = "PENDING";
    private static final String AUDIT_APPROVED = "APPROVED";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final String CACHE_PROFILE = "merchant:cache:profile:%d";
    private static final String CACHE_PRODUCTS = "merchant:cache:products:%d:%s";
    private static final String CACHE_COUPON_POOL = "merchant:cache:coupon-pool:%d";
    private static final String CACHE_ASSESSMENT = "merchant:cache:assessment:%d";
    private static final String IDEM_APPLICATION = "merchant:idem:application:%s";
    private static final String IDEM_COUPON = "merchant:idem:coupon:%s";
    private static final String IDEM_PROMOTION = "merchant:idem:promotion-code:%s";
    private static final String IDEM_STOCK = "merchant:idem:stock:%s";
    private static final String SOURCE_MERCHANT = "MERCHANT";
    private static final Set<String> SPECIAL_CATEGORIES = Set.of("餐饮", "酒店", "旅行社", "汽车救援");

    private final CurrentUserContext currentUser;
    private final MerchantProfileMapper profileMapper;
    private final MerchantProductMapper productMapper;
    private final MerchantCouponPoolMapper couponPoolMapper;
    private final MerchantRewardPoolConfigMapper rewardPoolMapper;
    private final MerchantPromotionCodeMapper promotionCodeMapper;
    private final MerchantPromotionStatsMapper promotionStatsMapper;
    private final MerchantUserRelationMapper userRelationMapper;
    private final MerchantAuditLogMapper auditLogMapper;
    private final MerchantPartnerService partnerService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${tongluxing.merchant.data-encryption-key}")
    private String encryptionKey;

    /** 提交商家入驻申请。 */
    @Override
    @Transactional
    public MerchantProfileVO submitApplication(MerchantApplicationRequest request, String requestId) {
        Long userId = currentUser.requireUserId();
        validateApplication(request);
        String idemKey = idemKey(IDEM_APPLICATION, requestId);
        MerchantProfileVO cached = idemGet(idemKey, MerchantProfileVO.class);
        if (cached != null) {
            return cached;
        }
        MerchantQueryDTO existing = profileMapper.findByUserId(userId);
        if (existing != null && !"REJECTED".equals(existing.getAuditStatus())) {
            throw new BusinessException(409, "当前账号已有待审核或已通过的商家申请");
        }

        LocalDateTime now = LocalDateTime.now();
        long merchantId = existing == null ? SnowflakeIdGenerator.nextId() : existing.getMerchantId();
        String detailsJson = writeJson(applicationDetails(request));
        if (existing != null) {
            profileMapper.resubmitApplication(merchantId, userId, trim(request.merchantName()), trim(request.category()),
                    trim(request.contactName()), encrypt(request.contactPhone()), maskPhone(request.contactPhone()),
                    trim(request.storeAddress()), request.longitude(), request.latitude(), trim(request.logoImageKey()),
                    trim(request.introduction()), trim(request.businessLicenseImageKey()), detailsJson, now);
            profileMapper.saveReview(merchantId, "", null, now);
            MerchantProfileVO result = profile(profileMapper.findById(merchantId));
            audit(merchantId, userId, "APPLICATION_RESUBMIT", "MERCHANT", merchantId, profile(existing), result, "商家入驻重新提交");
            idemPut(idemKey, result);
            return result;
        }
        profileMapper.insertApplication(
                merchantId,
                userId,
                trim(request.merchantName()),
                trim(request.category()),
                trim(request.contactName()),
                encrypt(request.contactPhone()),
                maskPhone(request.contactPhone()),
                "",
                "",
                trim(request.storeAddress()),
                request.longitude(),
                request.latitude(),
                trim(request.businessLicenseImageKey()),
                detailsJson,
                "",
                "",
                now);
        MerchantProfileVO result = profile(profileMapper.findById(merchantId));
        audit(merchantId, userId, "APPLICATION_SUBMIT", "MERCHANT", merchantId, null, result, "商家入驻申请");
        idemPut(idemKey, result);
        return result;
    }

    /** 特殊行业必须提交经营资质，门店坐标必须落在合法经纬度范围内。 */
    private void validateApplication(MerchantApplicationRequest request) {
        String category = trim(request.category());
        if (SPECIAL_CATEGORIES.contains(category)
                && (request.qualificationImageKeys() == null || request.qualificationImageKeys().isEmpty())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, category + "类目必须提交对应经营许可证");
        }
        if (request.latitude() == null || request.latitude().compareTo(BigDecimal.valueOf(-90)) < 0
                || request.latitude().compareTo(BigDecimal.valueOf(90)) > 0
                || request.longitude() == null || request.longitude().compareTo(BigDecimal.valueOf(-180)) < 0
                || request.longitude().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "门店经纬度不合法");
        }
    }

    @Override
    public PageResult<MerchantProfileVO> applicationsForAdmin(String status, int page, int size) {
        String normalized = trimToEmpty(status).toUpperCase();
        if (!normalized.isEmpty() && !List.of("PENDING", "APPROVED", "REJECTED").contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "申请状态不合法");
        }
        int p = Math.max(1, page);
        int s = Math.min(100, Math.max(1, size));
        List<MerchantProfileVO> records = profileMapper.findApplications(normalized, (p - 1) * s, s)
                .stream().map(this::profile).toList();
        return new PageResult<>(records, profileMapper.countApplications(normalized), p, s);
    }

    @Override
    public MerchantProfileVO applicationForAdmin(Long applicationId) {
        MerchantQueryDTO row = profileMapper.findById(applicationId);
        if (row == null) throw new BusinessException(ResultCode.NOT_FOUND, "商家入驻申请不存在");
        return profile(row);
    }

    @Override
    @Transactional
    public MerchantProfileVO auditApplication(Long applicationId, String result, String reason, Long reviewerId) {
        MerchantQueryDTO before = profileMapper.findById(applicationId);
        if (before == null) throw new BusinessException(ResultCode.NOT_FOUND, "商家入驻申请不存在");
        if (!AUDIT_PENDING.equals(before.getAuditStatus())) throw new BusinessException(409, "该申请已完成审核");
        LocalDateTime now = LocalDateTime.now();
        if (profileMapper.updateAuditStatus(applicationId, result, now) != 1) {
            throw new BusinessException(409, "申请状态已变化，请刷新后重试");
        }
        profileMapper.saveReview(applicationId, "REJECTED".equals(result) ? trim(reason) : "", reviewerId, now);
        if (AUDIT_APPROVED.equals(result)) profileMapper.grantMerchantRole(before.getUserId(), now);
        MerchantProfileVO after = profile(profileMapper.findById(applicationId));
        audit(applicationId, reviewerId, "APPLICATION_AUDIT", "MERCHANT", applicationId, profile(before), after,
                AUDIT_APPROVED.equals(result) ? "商家入驻审核通过" : "商家入驻审核驳回");
        evictMerchantCaches(applicationId);
        return after;
    }

    @Override
    @Transactional
    public void saveSettlementAccount(MerchantSettlementRequest request) {
        MerchantQueryDTO merchant = requireApprovedMerchant();
        profileMapper.saveSettlement(merchant.getMerchantId(), request.accountType(), trim(request.accountName()),
                encrypt(request.accountNo()), trim(request.bankName()), LocalDateTime.now());
        audit(merchant.getMerchantId(), currentUser.requireUserId(), "SETTLEMENT_ACCOUNT_SAVE", "MERCHANT",
                merchant.getMerchantId(), null, Map.of("accountType", request.accountType(), "bankName", request.bankName()),
                "审核通过后补充收款账户");
    }

    /** 查询当前商家资料。 */
    @Override
    public MerchantProfileVO currentProfile() {
        MerchantQueryDTO merchant = currentMerchant();
        String key = CACHE_PROFILE.formatted(merchant.getMerchantId());
        MerchantProfileVO cached = cacheGet(key, MerchantProfileVO.class);
        if (cached != null) {
            return cached;
        }
        MerchantProfileVO result = profile(merchant);
        cachePut(key, result, Duration.ofMinutes(30));
        return result;
    }

    /** 修改当前商家资料。 */
    @Override
    @Transactional
    public MerchantProfileVO updateCurrentProfile(UpdateMerchantProfileRequest request) {
        Long userId = currentUser.requireUserId();
        MerchantQueryDTO old = requireApprovedMerchant();
        String phone = StringUtils.hasText(request.contactPhone()) ? request.contactPhone().trim() : old.getContactPhoneMask();
        profileMapper.updateProfile(
                old.getMerchantId(),
                userId,
                value(request.merchantName(), old.getMerchantName()),
                value(request.category(), old.getCategory()),
                value(request.contactName(), old.getContactName()),
                StringUtils.hasText(request.contactPhone()) ? encrypt(request.contactPhone()) : null,
                StringUtils.hasText(request.contactPhone()) ? maskPhone(phone) : old.getContactPhoneMask(),
                value(request.address(), old.getAddress()),
                request.longitude() == null ? old.getLongitude() : request.longitude(),
                request.latitude() == null ? old.getLatitude() : request.latitude(),
                value(request.coverImageKey(), old.getCoverImageKey()),
                value(request.description(), old.getDescription()),
                LocalDateTime.now());
        evictMerchantCaches(old.getMerchantId());
        MerchantProfileVO result = profile(profileMapper.findById(old.getMerchantId()));
        audit(old.getMerchantId(), userId, "PROFILE_UPDATE", "MERCHANT", old.getMerchantId(), profile(old), result, "商家资料修改");
        return result;
    }

    /** 查询当前商家商品列表。 */
    @Override
    public PageResult<MerchantProductVO> products(String status, int page, int size) {
        MerchantQueryDTO merchant = requireApprovedMerchant();
        int normalizedPage = Math.max(1, page);
        int normalizedSize = Math.min(100, Math.max(1, size));
        String normalizedStatus = trimToEmpty(status);
        String key = CACHE_PRODUCTS.formatted(merchant.getMerchantId(), normalizedStatus.isBlank() ? "ALL" : normalizedStatus);
        List<MerchantProductVO> cached = cacheListGet(key, MerchantProductVO.class);
        if (cached != null) {
            return new PageResult<>(cached, productMapper.countByMerchant(merchant.getMerchantId(), normalizedStatus), normalizedPage, normalizedSize);
        }
        List<MerchantProductVO> records = productMapper.findByMerchant(
                merchant.getMerchantId(), normalizedStatus, (normalizedPage - 1) * normalizedSize, normalizedSize)
                .stream().map(this::product).toList();
        cachePut(key, records, Duration.ofMinutes(10));
        return new PageResult<>(records, productMapper.countByMerchant(merchant.getMerchantId(), normalizedStatus), normalizedPage, normalizedSize);
    }

    /** 发布拼团商品。 */
    @Override
    @Transactional
    public MerchantProductVO createProduct(CreateMerchantProductRequest request) {
        Long userId = currentUser.requireUserId();
        MerchantQueryDTO merchant = requireApprovedMerchant();
        validatePrice(request.originalPrice(), request.groupPrice());
        long productId = SnowflakeIdGenerator.nextId();
        productMapper.insertProduct(
                productId,
                merchant.getMerchantId(),
                trim(request.productName()),
                trim(request.productType()),
                request.originalPrice(),
                request.groupPrice(),
                trimToEmpty(request.ladderPriceJson()),
                request.targetPeople(),
                request.stock(),
                request.validHours(),
                request.minSettlementPrice(),
                writeJson(request.imageKeys() == null ? List.of() : request.imageKeys()),
                trimToEmpty(request.description()),
                LocalDateTime.now());
        evictProductCaches(merchant.getMerchantId());
        MerchantProductVO result = product(productMapper.findById(merchant.getMerchantId(), productId));
        audit(merchant.getMerchantId(), userId, "PRODUCT_CREATE", "PRODUCT", productId, null, result, "发布拼团商品");
        return result;
    }

    /** 编辑拼团商品。 */
    @Override
    @Transactional
    public MerchantProductVO updateProduct(Long productId, UpdateMerchantProductRequest request) {
        Long userId = currentUser.requireUserId();
        MerchantQueryDTO merchant = requireApprovedMerchant();
        MerchantQueryDTO old = productMapper.findById(merchant.getMerchantId(), productId);
        if (old == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        BigDecimal originalPrice = request.originalPrice() == null ? old.getOriginalPrice() : request.originalPrice();
        BigDecimal groupPrice = request.groupPrice() == null ? old.getGroupPrice() : request.groupPrice();
        validatePrice(originalPrice, groupPrice);
        productMapper.updateProduct(
                merchant.getMerchantId(),
                productId,
                value(request.productName(), old.getProductName()),
                value(request.productType(), old.getProductType()),
                originalPrice,
                groupPrice,
                request.ladderPriceJson() == null ? old.getLadderPriceJson() : request.ladderPriceJson().trim(),
                request.targetPeople() == null ? old.getTargetPeople() : request.targetPeople(),
                request.stock() == null ? old.getStock() : request.stock(),
                request.validHours() == null ? old.getValidHours() : request.validHours(),
                request.minSettlementPrice() == null ? old.getMinSettlementPrice() : request.minSettlementPrice(),
                request.imageKeys() == null ? old.getImageKeysJson() : writeJson(request.imageKeys()),
                value(request.description(), old.getDescription()),
                LocalDateTime.now());
        evictProductCaches(merchant.getMerchantId());
        MerchantProductVO result = product(productMapper.findById(merchant.getMerchantId(), productId));
        audit(merchant.getMerchantId(), userId, "PRODUCT_UPDATE", "PRODUCT", productId, product(old), result, "编辑拼团商品");
        return result;
    }

    /** 下架拼团商品。 */
    @Override
    @Transactional
    public MerchantProductVO offShelfProduct(Long productId) {
        Long userId = currentUser.requireUserId();
        MerchantQueryDTO merchant = requireApprovedMerchant();
        MerchantQueryDTO old = productMapper.findById(merchant.getMerchantId(), productId);
        if (old == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        productMapper.offShelf(merchant.getMerchantId(), productId, LocalDateTime.now());
        evictProductCaches(merchant.getMerchantId());
        MerchantProductVO result = product(productMapper.findById(merchant.getMerchantId(), productId));
        audit(merchant.getMerchantId(), userId, "PRODUCT_OFF_SHELF", "PRODUCT", productId, product(old), result, "下架拼团商品");
        return result;
    }

    /**
     * 读取商品快照，供拼团和订单模块创建本地事实数据时使用。
     */
    @Override
    public MerchantProductVO productSnapshot(Long productId) {
        MerchantQueryDTO product = productMapper.findSnapshotByProductId(productId);
        if (product == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在或未上架");
        }
        return product(product);
    }

    /**
     * 扣减商品库存。requestId 用于跨模块重试幂等，避免重复扣库存。
     */
    @Override
    @Transactional
    public void decreaseProductStock(Long productId, Integer quantity, String requestId) {
        String idemKey = idemKey(IDEM_STOCK, requestId);
        if (Boolean.TRUE.equals(redis.hasKey(idemKey))) {
            return;
        }
        int affected = productMapper.decreaseStock(productId, Math.max(quantity == null ? 1 : quantity, 1),
                LocalDateTime.now());
        if (affected == 0) {
            throw new BusinessException("商品库存不足或已下架");
        }
        redis.opsForValue().set(idemKey, "SUCCESS", Duration.ofDays(7));
    }

    /** 查询商家券池列表。 */
    @Override
    public List<MerchantCouponPoolVO> couponPools() {
        MerchantQueryDTO merchant = requireApprovedMerchant();
        String key = CACHE_COUPON_POOL.formatted(merchant.getMerchantId());
        List<MerchantCouponPoolVO> cached = cacheListGet(key, MerchantCouponPoolVO.class);
        if (cached != null) {
            return cached;
        }
        List<MerchantCouponPoolVO> result = couponPoolMapper.findByMerchant(merchant.getMerchantId())
                .stream().map(this::couponPool).toList();
        cachePut(key, result, Duration.ofMinutes(10));
        return result;
    }

    /** 创建商家券池配置。 */
    @Override
    @Transactional
    public MerchantCouponPoolVO createCouponPool(CreateMerchantCouponPoolRequest request, String requestId) {
        Long userId = currentUser.requireUserId();
        MerchantQueryDTO merchant = requireApprovedMerchant();
        if (!partnerService.isApprovedPartner(merchant.getMerchantId())) {
            throw new BusinessException(403, "仅审核通过的合作商可以提交合作商券");
        }
        String idemKey = idemKey(IDEM_COUPON, requestId);
        MerchantCouponPoolVO cached = idemGet(idemKey, MerchantCouponPoolVO.class);
        if (cached != null) {
            return cached;
        }
        validateCouponPool(request);
        long poolId = SnowflakeIdGenerator.nextId();
        couponPoolMapper.insertPool(
                poolId,
                merchant.getMerchantId(),
                trim(request.couponName()),
                trim(request.couponType()),
                "PARTNER",
                request.thresholdAmount() == null ? BigDecimal.ZERO : request.thresholdAmount(),
                request.discountAmount() == null ? BigDecimal.ZERO : request.discountAmount(),
                request.discountRate() == null ? BigDecimal.ZERO : request.discountRate(),
                request.totalStock(),
                request.validDays(),
                trim(request.settlementMode()),
                LocalDateTime.now());
        cacheDelete(CACHE_COUPON_POOL.formatted(merchant.getMerchantId()));
        MerchantCouponPoolVO result = couponPool(couponPoolMapper.findById(merchant.getMerchantId(), poolId));
        audit(merchant.getMerchantId(), userId, "COUPON_POOL_CREATE", "COUPON_POOL", poolId, null, result, "创建商家券池");
        idemPut(idemKey, result);
        return result;
    }

    /** 加入或退出奖励合作商家池。 */
    @Override
    @Transactional
    public MerchantRewardPoolVO updateRewardPool(UpdateRewardPoolRequest request) {
        Long userId = currentUser.requireUserId();
        MerchantQueryDTO merchant = requireApprovedMerchant();
        BigDecimal bonus = Boolean.TRUE.equals(request.enabled()) ? new BigDecimal("0.1000") : BigDecimal.ZERO;
        MerchantQueryDTO old = rewardPoolMapper.findByMerchant(merchant.getMerchantId());
        if (old == null) {
            rewardPoolMapper.insertConfig(SnowflakeIdGenerator.nextId(), merchant.getMerchantId(),
                    request.enabled(), trimToEmpty(request.couponType()), request.monthlyStock() == null ? 0 : request.monthlyStock(),
                    bonus, LocalDateTime.now());
        } else {
            rewardPoolMapper.updateConfig(merchant.getMerchantId(), request.enabled(),
                    value(request.couponType(), old.getCouponType()), request.monthlyStock() == null ? old.getMonthlyStock() : request.monthlyStock(),
                    bonus, LocalDateTime.now());
        }
        evictMerchantCaches(merchant.getMerchantId());
        MerchantRewardPoolVO result = rewardPool(rewardPoolMapper.findByMerchant(merchant.getMerchantId()));
        audit(merchant.getMerchantId(), userId, "REWARD_POOL_UPDATE", "REWARD_POOL", merchant.getMerchantId(), old == null ? null : rewardPool(old), result, "更新奖励合作商家池");
        return result;
    }

    /** 创建推广码。 */
    @Override
    @Transactional
    public MerchantPromotionCodeVO createPromotionCode(CreatePromotionCodeRequest request, String requestId) {
        Long userId = currentUser.requireUserId();
        MerchantQueryDTO merchant = requireApprovedMerchant();
        String idemKey = idemKey(IDEM_PROMOTION, requestId);
        MerchantPromotionCodeVO cached = idemGet(idemKey, MerchantPromotionCodeVO.class);
        if (cached != null) {
            return cached;
        }
        long id = SnowflakeIdGenerator.nextId();
        String code = promotionCode(merchant.getMerchantId(), id);
        try {
            promotionCodeMapper.insertCode(id, merchant.getMerchantId(), code, trim(request.channelName()),
                    trim(request.scene()), "merchant/promotion/" + code, trimToEmpty(request.remark()), LocalDateTime.now());
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "推广码生成冲突，请重试");
        }
        MerchantPromotionCodeVO result = promotionCode(promotionCodeMapper.findById(merchant.getMerchantId(), id));
        audit(merchant.getMerchantId(), userId, "PROMOTION_CODE_CREATE", "PROMOTION_CODE", id, null, result, "创建推广码");
        idemPut(idemKey, result);
        return result;
    }

    /** 查询推广码列表。 */
    @Override
    public List<MerchantPromotionCodeVO> promotionCodes() {
        MerchantQueryDTO merchant = requireApprovedMerchant();
        return promotionCodeMapper.findByMerchant(merchant.getMerchantId()).stream().map(this::promotionCode).toList();
    }

    /** 查询推广码聚合统计。 */
    @Override
    public MerchantPromotionStatsVO promotionStats(Long promotionId) {
        MerchantQueryDTO merchant = requireApprovedMerchant();
        if (promotionCodeMapper.findById(merchant.getMerchantId(), promotionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "推广码不存在");
        }
        MerchantQueryDTO stats = promotionStatsMapper.aggregateByPromotion(merchant.getMerchantId(), promotionId);
        long registerCount = userRelationMapper.countByPromotion(merchant.getMerchantId(), promotionId);
        return new MerchantPromotionStatsVO(promotionId, registerCount,
                stats.getCouponClaimCount(), stats.getCouponVerifyCount(),
                stats.getOrderCount(), stats.getTradeAmount());
    }

    /** 首次注册成功后，只处理 MERCHANT 来源，建立商家推广用户关系。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegistered(UserRegisteredEvent event) {
        if (event == null || !event.hasSource(SOURCE_MERCHANT)) {
            return;
        }
        MerchantQueryDTO promotion = promotionCodeMapper.findActiveByCode(event.normalizedSourceCode());
        if (promotion == null || event.userId() == null) {
            return;
        }
        if (userRelationMapper.findRelationIdByUserId(event.userId()) != null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        try {
            userRelationMapper.insertRelation(
                    SnowflakeIdGenerator.nextId(),
                    promotion.getMerchantId(),
                    promotion.getId(),
                    promotion.getPromotionCode(),
                    event.userId(),
                    event.registerTime(),
                    now);
        } catch (DuplicateKeyException ignored) {
            // 商家推广用户关系以 user_id 唯一约束兜底，重复事件不覆盖首条关系。
        }
    }

    /** 查询商家考核中心。 */
    @Override
    public MerchantAssessmentVO assessment() {
        MerchantQueryDTO merchant = requireApprovedMerchant();
        String key = CACHE_ASSESSMENT.formatted(merchant.getMerchantId());
        MerchantAssessmentVO cached = cacheGet(key, MerchantAssessmentVO.class);
        if (cached != null) {
            return cached;
        }
        MerchantAssessmentVO result = assessment(merchant);
        cachePut(key, result, Duration.ofMinutes(30));
        return result;
    }

    private MerchantQueryDTO currentMerchant() {
        Long userId = currentUser.requireUserId();
        MerchantQueryDTO merchant = profileMapper.findByUserId(userId);
        if (merchant == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "当前账号尚未入驻商家");
        }
        return merchant;
    }

    private MerchantQueryDTO requireApprovedMerchant() {
        MerchantQueryDTO merchant = currentMerchant();
        if (!AUDIT_APPROVED.equals(merchant.getAuditStatus())) {
            throw new BusinessException(409, "商家未审核通过，暂不可操作");
        }
        if (!STATUS_ACTIVE.equals(merchant.getStatus())) {
            throw new BusinessException(409, "商家已被禁用，暂不可操作");
        }
        return merchant;
    }

    private MerchantProfileVO profile(MerchantQueryDTO row) {
        return new MerchantProfileVO(row.getMerchantId(), row.getMerchantName(), row.getCategory(),
                row.getAuditStatus(), row.getMerchantLevel(), row.getCommissionRate(),
                row.getRankWeight(), row.getExclusionRadiusKm(), row.getRejectReason(),
                readMap(row.getQualificationJson()), row.getCreatedAt(), row.getReviewedAt());
    }

    private Map<String, Object> applicationDetails(MerchantApplicationRequest request) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("merchantShortName", trim(request.merchantShortName()));
        v.put("logoImageKey", trim(request.logoImageKey()));
        v.put("introduction", trim(request.introduction()));
        v.put("businessLicenseImageKey", trim(request.businessLicenseImageKey()));
        v.put("contactName", trim(request.contactName()));
        v.put("contactPhoneMask", maskPhone(request.contactPhone()));
        v.put("contactEmail", trim(request.contactEmail()));
        v.put("contactWechat", trimToEmpty(request.contactWechat()));
        v.put("storeName", trim(request.storeName()));
        v.put("storeAddress", trim(request.storeAddress()));
        v.put("longitude", request.longitude());
        v.put("latitude", request.latitude());
        v.put("storefrontImageKey", trim(request.storefrontImageKey()));
        v.put("interiorImageKeys", request.interiorImageKeys() == null ? List.of() : request.interiorImageKeys());
        v.put("legalRepresentativeName", trim(request.legalRepresentativeName()));
        v.put("legalIdFrontImageKey", trim(request.legalIdFrontImageKey()));
        v.put("legalIdBackImageKey", trim(request.legalIdBackImageKey()));
        v.put("qualificationImageKeys", request.qualificationImageKeys() == null ? List.of() : request.qualificationImageKeys());
        return v;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) return Map.of();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private MerchantProductVO product(MerchantQueryDTO row) {
        return new MerchantProductVO(row.getId(), row.getMerchantId(), row.getProductName(), row.getProductType(),
                row.getOriginalPrice(), row.getGroupPrice(), row.getLadderPriceJson(), row.getTargetPeople(),
                row.getStock(), row.getValidHours(), row.getMinSettlementPrice(),
                readStringList(row.getImageKeysJson()), row.getDescription(), row.getProductStatus(), row.getUpdatedAt());
    }

    private MerchantCouponPoolVO couponPool(MerchantQueryDTO row) {
        return new MerchantCouponPoolVO(row.getId(), row.getMerchantId(), row.getCouponName(), row.getCouponType(),
                row.getSourceType(), row.getThresholdAmount(), row.getDiscountAmount(), row.getDiscountRate(),
                row.getTotalStock(), row.getUsedStock(), row.getValidDays(), row.getSettlementMode(),
                row.getAuditStatus(), row.getPoolStatus());
    }

    private MerchantRewardPoolVO rewardPool(MerchantQueryDTO row) {
        return new MerchantRewardPoolVO(row.getMerchantId(), row.getEnabled(), row.getCouponType(),
                row.getMonthlyStock(), row.getUsedStock(), row.getExposureWeightBonus());
    }

    private MerchantPromotionCodeVO promotionCode(MerchantQueryDTO row) {
        return new MerchantPromotionCodeVO(row.getId(), row.getMerchantId(), row.getPromotionCode(),
                row.getChannelName(), row.getScene(), row.getQrImageKey(), row.getStatus(), row.getRemark(), row.getCreatedAt());
    }

    private MerchantAssessmentVO assessment(MerchantQueryDTO row) {
        BigDecimal score = row.getScore() == null ? BigDecimal.ZERO : row.getScore();
        Level next = nextLevel(score);
        return new MerchantAssessmentVO(row.getMerchantLevel(), score, row.getCommissionRate(),
                row.getRankWeight(), row.getExclusionRadiusKm(), next.level(), next.needScore());
    }

    private void validatePrice(BigDecimal originalPrice, BigDecimal groupPrice) {
        if (originalPrice == null || groupPrice == null || originalPrice.signum() <= 0 || groupPrice.signum() <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品价格必须大于0");
        }
        if (groupPrice.compareTo(originalPrice) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "拼团价不能高于原价");
        }
    }

    private void validateCouponPool(CreateMerchantCouponPoolRequest request) {
        if (!List.of("MERCHANT_NEWBIE", "REWARD_POOL", "PARTNER").contains(request.sourceType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "券来源类型不合法");
        }
        if (!List.of("MERCHANT_DEDUCT", "PLATFORM_SUBSIDY", "MONTHLY_RECONCILE", "FREE_SPONSOR").contains(request.settlementMode())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "结算方式不合法");
        }
        boolean hasAmount = request.discountAmount() != null && request.discountAmount().signum() > 0;
        boolean hasRate = request.discountRate() != null && request.discountRate().signum() > 0;
        if (!hasAmount && !hasRate) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "优惠金额或折扣至少填写一个");
        }
    }

    private void audit(Long merchantId, Long operatorId, String operationType, String targetType,
                       Long targetId, Object before, Object after, String remark) {
        try {
            auditLogMapper.insertLog(SnowflakeIdGenerator.nextId(), merchantId, operatorId, operationType, targetType,
                    targetId, before == null ? "" : writeJson(before), after == null ? "" : writeJson(after),
                    remark, LocalDateTime.now());
        } catch (RuntimeException ignored) {
            // 审计失败不阻断主业务，避免用户操作因日志系统异常被回滚。
        }
    }

    private String encrypt(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "商家敏感数据加密失败");
        }
    }

    private String maskPhone(String phone) {
        String value = trim(phone);
        return value.length() < 11 ? value : value.substring(0, 3) + "****" + value.substring(7);
    }

    private String promotionCode(Long merchantId, Long id) {
        return "M" + Long.toUnsignedString(merchantId, 36).toUpperCase()
                + Long.toUnsignedString(id % 1_000_000L, 36).toUpperCase();
    }

    private String value(String candidate, String old) {
        return candidate == null ? old : candidate.trim();
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "JSON序列化失败");
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, String.class);
            return objectMapper.readValue(json, type);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private <T> T cacheGet(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private <T> List<T> cacheListGet(String key, Class<T> elementType) {
        try {
            String value = redis.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            return objectMapper.readValue(value, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void cachePut(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // 缓存失败不阻断主流程。
        }
    }

    private void cacheDelete(String key) {
        try {
            redis.delete(key);
        } catch (Exception ignored) {
            // 缓存失败不阻断主流程。
        }
    }

    private void evictMerchantCaches(Long merchantId) {
        cacheDelete(CACHE_PROFILE.formatted(merchantId));
        cacheDelete(CACHE_ASSESSMENT.formatted(merchantId));
    }

    private void evictProductCaches(Long merchantId) {
        try {
            Set<String> keys = redis.keys("merchant:cache:products:%d:*".formatted(merchantId));
            if (keys != null && !keys.isEmpty()) {
                redis.delete(keys);
            }
        } catch (Exception ignored) {
            // 缓存失败不阻断主流程。
        }
    }

    private String idemKey(String pattern, String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }
        return pattern.formatted(requestId.trim());
    }

    private <T> T idemGet(String key, Class<T> type) {
        return key == null ? null : cacheGet(key, type);
    }

    private void idemPut(String key, Object value) {
        if (key != null) {
            cachePut(key, value, Duration.ofHours(24));
        }
    }

    private Level nextLevel(BigDecimal score) {
        if (score.compareTo(new BigDecimal("60")) < 0) {
            return new Level("L2", new BigDecimal("60").subtract(score));
        }
        if (score.compareTo(new BigDecimal("75")) < 0) {
            return new Level("L3", new BigDecimal("75").subtract(score));
        }
        if (score.compareTo(new BigDecimal("85")) < 0) {
            return new Level("L4", new BigDecimal("85").subtract(score));
        }
        if (score.compareTo(new BigDecimal("95")) < 0) {
            return new Level("L5", new BigDecimal("95").subtract(score));
        }
        return new Level(null, BigDecimal.ZERO);
    }

    private record Level(String level, BigDecimal needScore) {
    }
}
