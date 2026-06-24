package com.tongdao.merchant.service.impl;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.merchant.dto.CreateMerchantCouponPoolRequest;
import com.tongdao.merchant.dto.CreateMerchantProductRequest;
import com.tongdao.merchant.dto.CreatePromotionCodeRequest;
import com.tongdao.merchant.dto.MerchantApplicationRequest;
import com.tongdao.merchant.dto.MerchantQueryDTO;
import com.tongdao.merchant.dto.UpdateMerchantProductRequest;
import com.tongdao.merchant.dto.UpdateMerchantProfileRequest;
import com.tongdao.merchant.dto.UpdateRewardPoolRequest;
import com.tongdao.merchant.mapper.MerchantAuditLogMapper;
import com.tongdao.merchant.mapper.MerchantCouponPoolMapper;
import com.tongdao.merchant.mapper.MerchantProductMapper;
import com.tongdao.merchant.mapper.MerchantProfileMapper;
import com.tongdao.merchant.mapper.MerchantPromotionCodeMapper;
import com.tongdao.merchant.mapper.MerchantPromotionStatsMapper;
import com.tongdao.merchant.mapper.MerchantRewardPoolConfigMapper;
import com.tongdao.merchant.service.MerchantService;
import com.tongdao.merchant.vo.MerchantAssessmentVO;
import com.tongdao.merchant.vo.MerchantCouponPoolVO;
import com.tongdao.merchant.vo.MerchantProductVO;
import com.tongdao.merchant.vo.MerchantProfileVO;
import com.tongdao.merchant.vo.MerchantPromotionCodeVO;
import com.tongdao.merchant.vo.MerchantPromotionStatsVO;
import com.tongdao.merchant.vo.MerchantRewardPoolVO;
import com.tongdao.merchant.vo.PageResult;
import com.tongdao.user.support.CurrentUserContext;

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

    private final CurrentUserContext currentUser;
    private final MerchantProfileMapper profileMapper;
    private final MerchantProductMapper productMapper;
    private final MerchantCouponPoolMapper couponPoolMapper;
    private final MerchantRewardPoolConfigMapper rewardPoolMapper;
    private final MerchantPromotionCodeMapper promotionCodeMapper;
    private final MerchantPromotionStatsMapper promotionStatsMapper;
    private final MerchantAuditLogMapper auditLogMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${tongdao.merchant.data-encryption-key:change-this-merchant-data-key}")
    private String encryptionKey;

    /** 提交商家入驻申请。 */
    @Override
    @Transactional
    public MerchantProfileVO submitApplication(MerchantApplicationRequest request, String requestId) {
        Long userId = currentUser.requireUserId();
        String idemKey = idemKey(IDEM_APPLICATION, requestId);
        MerchantProfileVO cached = idemGet(idemKey, MerchantProfileVO.class);
        if (cached != null) {
            return cached;
        }
        if (profileMapper.findByUserId(userId) != null) {
            throw new BusinessException(409, "当前账号已提交或已拥有商家资料");
        }

        LocalDateTime now = LocalDateTime.now();
        long merchantId = SnowflakeIdGenerator.nextId();
        profileMapper.insertApplication(
                merchantId,
                userId,
                trim(request.merchantName()),
                trim(request.category()),
                trim(request.contactName()),
                encrypt(request.contactPhone()),
                maskPhone(request.contactPhone()),
                trimToEmpty(request.provinceCode()),
                trimToEmpty(request.cityCode()),
                trim(request.address()),
                request.longitude(),
                request.latitude(),
                trim(request.licenseImageKey()),
                writeJson(request.qualificationImageKeys() == null ? List.of() : request.qualificationImageKeys()),
                StringUtils.hasText(request.bankAccountNo()) ? encrypt(request.bankAccountNo()) : "",
                trimToEmpty(request.bankName()),
                now);
        MerchantProfileVO result = profile(profileMapper.findById(merchantId));
        audit(merchantId, userId, "APPLICATION_SUBMIT", "MERCHANT", merchantId, null, result, "商家入驻申请");
        idemPut(idemKey, result);
        return result;
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
        MerchantQueryDTO old = currentMerchant();
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
        MerchantQueryDTO merchant = currentMerchant();
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
        MerchantQueryDTO merchant = currentMerchant();
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
        MerchantQueryDTO merchant = currentMerchant();
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

    /** 查询商家券池列表。 */
    @Override
    public List<MerchantCouponPoolVO> couponPools() {
        MerchantQueryDTO merchant = currentMerchant();
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
                trim(request.sourceType()),
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
        MerchantQueryDTO merchant = currentMerchant();
        return promotionCodeMapper.findByMerchant(merchant.getMerchantId()).stream().map(this::promotionCode).toList();
    }

    /** 查询推广码聚合统计。 */
    @Override
    public MerchantPromotionStatsVO promotionStats(Long promotionId) {
        MerchantQueryDTO merchant = currentMerchant();
        if (promotionCodeMapper.findById(merchant.getMerchantId(), promotionId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "推广码不存在");
        }
        MerchantQueryDTO stats = promotionStatsMapper.aggregateByPromotion(merchant.getMerchantId(), promotionId);
        return new MerchantPromotionStatsVO(promotionId, stats.getExposureCount(), stats.getClickCount(),
                stats.getRegisterCount(), stats.getCouponClaimCount(), stats.getCouponVerifyCount(),
                stats.getOrderCount(), stats.getTradeAmount());
    }

    /** 查询商家考核中心。 */
    @Override
    public MerchantAssessmentVO assessment() {
        MerchantQueryDTO merchant = currentMerchant();
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
                row.getRankWeight(), row.getExclusionRadiusKm());
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
        if (!List.of("MERCHANT_NEWBIE", "REWARD_POOL").contains(request.sourceType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "券来源类型不合法");
        }
        if (!List.of("MERCHANT_DEDUCT", "PLATFORM_SUBSIDY", "MONTHLY_RECONCILE").contains(request.settlementMode())) {
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
