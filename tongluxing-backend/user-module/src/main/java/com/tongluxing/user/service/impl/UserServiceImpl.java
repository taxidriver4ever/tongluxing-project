package com.tongluxing.user.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.user.mapper.UserDomainMapper;
import com.tongluxing.user.dto.UserQueryDTO;
import com.tongluxing.user.model.UserModels.CertificationRequest;
import com.tongluxing.user.model.UserModels.CertificationVO;
import com.tongluxing.user.model.UserModels.DrivingLicenseAuditDetailVO;
import com.tongluxing.user.model.UserModels.DrivingLicenseAuditSummaryVO;
import com.tongluxing.user.model.UserModels.PageResult;
import com.tongluxing.user.model.UserModels.PublicProfileVO;
import com.tongluxing.user.model.UserModels.UpdateUserProfileRequest;
import com.tongluxing.user.model.UserModels.UserProfileVO;
import com.tongluxing.user.service.UserService;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 用户模块业务实现。
 *
 * <p>主要负责用户资料读写、驾驶证认证、公开主页隐私控制，以及 Redis 缓存维护。
 * 默认资料和隐私设置采用懒初始化方式，避免注册流程必须一次性写入所有用户域数据。</p>
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    /** 用于 AES-GCM 加密时生成随机 IV。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    /** 当前用户完整资料缓存 Key。 */
    private static final String PROFILE_CACHE = "user:cache:profile:%d";

    /** 用户公开资料缓存 Key。 */
    private static final String PUBLIC_CACHE = "user:cache:public-card:%d";

    private final CurrentUserContext currentUserContext;
    private final UserDomainMapper mapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${tongluxing.user.data-encryption-key:change-this-user-data-key}")
    private String encryptionKey;

    /**
     * 查询当前登录用户资料。
     *
     * <p>优先读取 Redis 缓存；缓存不存在时查询数据库并回填缓存。</p>
     */
    @Override
    public UserProfileVO getCurrentProfile() {
        long userId = currentUserContext.requireUserId();
        UserProfileVO cached = cacheGet(PROFILE_CACHE.formatted(userId), UserProfileVO.class);
        if (cached != null) {
            return cached;
        }
        UserProfileVO result = profile(userId);
        cachePut(PROFILE_CACHE.formatted(userId), result, Duration.ofMinutes(30));
        return result;
    }

    /**
     * 修改当前登录用户资料。
     *
     * <p>请求中的空字段表示“不修改”，因此会先读取旧资料再合并新值。
     * 修改后清理完整资料和公开资料缓存，保证后续读取到最新数据。</p>
     */
    @Override
    @Transactional
    public UserProfileVO updateCurrentProfile(UpdateUserProfileRequest request) {
        long userId = currentUserContext.requireUserId();
        UserProfileVO old = profile(userId);
        mapper.updateProfile(userId, value(request.nickname(), old.nickname()), value(request.avatarImageKey(), old.avatarImageKey()),
                request.gender() == null ? old.gender() : request.gender(), request.birthday() == null ? old.birthday() : request.birthday(),
                value(request.cityCode(), old.cityCode()), value(request.cityName(), old.cityName()),
                value(request.bio(), old.bio()), LocalDateTime.now());
        redis.delete(java.util.List.of(PROFILE_CACHE.formatted(userId), PUBLIC_CACHE.formatted(userId)));
        return profile(userId);
    }

    /** 提交驾驶证认证申请。 */
    @Override
    @Transactional
    public CertificationVO submitCertification(CertificationRequest request) {
        long userId = currentUserContext.requireUserId();
        ensureProfile(userId);
        UserQueryDTO latest = mapper.findLatestCertification(userId);
        if (latest != null && java.util.List.of("PENDING", "APPROVED").contains(latest.getCertificationStatus())) {
            throw new BusinessException(409, "认证正在审核或已通过");
        }
        LocalDateTime now = LocalDateTime.now();
        validateDates(request);
        mapper.insertCertification(SnowflakeIdGenerator.nextId(), userId,
                encrypt(request.holderName().trim()), encrypt(request.licenseNo().trim()),
                maskLicenseNo(request.licenseNo()), request.vehicleClass().trim(), request.firstIssueDate(),
                request.validFrom(), request.validTo(), trimToEmpty(request.issuingAuthority()),
                request.licenseFrontImageKey().trim(), trimToEmpty(request.licenseBackImageKey()),
                request.recognitionSource().trim(), now);
        redis.delete(java.util.List.of(PROFILE_CACHE.formatted(userId), PUBLIC_CACHE.formatted(userId)));
        return certification(mapper.findLatestCertification(userId));
    }

    /** 查询当前用户最近一次驾驶证认证状态。 */
    @Override
    public CertificationVO getLatestCertification() {
        long userId = currentUserContext.requireUserId();
        UserQueryDTO latest = mapper.findLatestCertification(userId);
        if (latest == null) {
            return new CertificationVO(null, userId, "UNSUBMITTED", null, null, null, true);
        }
        return certification(latest);
    }

    /** 后台分页查询驾驶证认证申请。 */
    @Override
    public PageResult<DrivingLicenseAuditSummaryVO> pageDrivingLicenseCertifications(
            String status, String keyword, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        String normalizedStatus = normalizeStatusFilter(status);
        String normalizedKeyword = trimToEmpty(keyword);
        List<DrivingLicenseAuditSummaryVO> records = mapper.pageCertifications(
                        normalizedStatus, normalizedKeyword, (normalizedPage - 1) * normalizedSize, normalizedSize)
                .stream()
                .map(row -> new DrivingLicenseAuditSummaryVO(
                        row.getId(), row.getUserId(), decrypt(row.getHolderNameCipher()), row.getLicenseNoMask(),
                        row.getVehicleClass(), row.getValidTo(), row.getCertificationStatus(), row.getSubmittedAt()))
                .toList();
        long total = mapper.countCertifications(normalizedStatus, normalizedKeyword);
        return new PageResult<>(records, total, normalizedPage, normalizedSize);
    }

    /** 后台查询驾驶证认证详情。 */
    @Override
    public DrivingLicenseAuditDetailVO getDrivingLicenseCertificationForAudit(Long certificationId) {
        UserQueryDTO row = mapper.findCertificationById(certificationId);
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "驾驶证认证申请不存在");
        }
        return auditDetail(row);
    }

    /** 后台应用驾驶证人工审核结果。 */
    @Override
    @Transactional
    public DrivingLicenseAuditDetailVO applyDrivingLicenseAuditResult(
            Long certificationId, String auditResult, String rejectReason, Long operatorId) {
        String normalizedResult = normalizeAuditResult(auditResult);
        String normalizedReason = normalizeRejectReason(normalizedResult, rejectReason);
        UserQueryDTO before = mapper.findCertificationById(certificationId);
        if (before == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "驾驶证认证申请不存在");
        }
        if (!"PENDING".equals(before.getCertificationStatus())) {
            throw new BusinessException(409, "驾驶证认证申请已审核");
        }
        int changed = mapper.updateCertificationAudit(
                certificationId, normalizedResult, normalizedReason, operatorId, LocalDateTime.now());
        if (changed != 1) {
            throw new BusinessException(409, "驾驶证认证状态已发生变化，请刷新后重试");
        }
        redis.delete(List.of(PROFILE_CACHE.formatted(before.getUserId()), PUBLIC_CACHE.formatted(before.getUserId())));
        return auditDetail(mapper.findCertificationById(certificationId));
    }

    /**
     * 查询用户公开主页资料。
     *
     * <p>公开主页受隐私设置控制：当 profileVisibility 为 PRIVATE 时，
     * 对外表现为“用户主页不存在”，避免泄露该用户是否存在或主动隐藏的信息。</p>
     */
    @Override
    public PublicProfileVO getPublicProfile(Long userId) {
        PublicProfileVO cached = cacheGet(PUBLIC_CACHE.formatted(userId), PublicProfileVO.class);
        if (cached != null) {
            return cached;
        }
        UserQueryDTO row = mapper.findProfile(userId);
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户主页不存在");
        }
        UserQueryDTO privacy = ensurePrivacy(userId);
        if ("PRIVATE".equals(privacy.getProfileVisibility())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户主页不存在");
        }
        UserProfileVO profile = profile(row);
        PublicProfileVO result = new PublicProfileVO(profile.userId(), profile.nickname(), profile.avatarImageKey(),
                profile.cityName(), profile.bio(), profile.drivingLicenseCertificationStatus(),
                row.getTotalTripCount(), row.getTotalDistanceMeters(), row.getTotalDurationMinutes(),
                row.getCompletedWaypointCount());
        cachePut(PUBLIC_CACHE.formatted(userId), result, Duration.ofMinutes(15));
        return result;
    }

    /**
     * 查询用户完整资料，必要时先初始化默认资料和隐私设置。
     */
    private UserProfileVO profile(long userId) {
        ensureProfile(userId);
        return profile(mapper.findProfile(userId));
    }

    /**
     * 将数据库查询对象转换为接口返回的用户资料 VO。
     */
    private UserProfileVO profile(UserQueryDTO row) {
        return new UserProfileVO(row.getUserId(), row.getNickname(), row.getAvatarImageKey(), row.getGender(),
                row.getBirthday(), row.getCityCode(), row.getCityName(), row.getBio(),
                row.getProfileStatus(), row.getCertificationStatus());
    }

    /**
     * 确保用户资料记录存在。
     *
     * <p>并发首次访问时可能同时插入默认资料，唯一键冲突可以安全忽略，
     * 因为说明其他请求已经完成初始化。</p>
     */
    private void ensureProfile(long userId) {
        if (mapper.findProfile(userId) == null) {
            try {
                mapper.insertProfile(SnowflakeIdGenerator.nextId(), userId, LocalDateTime.now());
            } catch (DuplicateKeyException ignored) {
                // 其他并发请求已创建默认资料，后续查询可以直接使用。
            }
        }
        ensurePrivacy(userId);
    }

    /**
     * 确保用户隐私设置存在，不存在时创建默认公开配置。
     */
    private UserQueryDTO ensurePrivacy(long userId) {
        UserQueryDTO row = mapper.findPrivacy(userId);
        if (row != null) {
            return row;
        }
        try {
            mapper.insertPrivacy(SnowflakeIdGenerator.nextId(), userId, LocalDateTime.now());
        } catch (DuplicateKeyException ignored) {
            // 其他并发请求已创建隐私设置，重新查询即可。
        }
        return mapper.findPrivacy(userId);
    }

    /**
     * 将驾驶证认证查询结果转换为接口返回对象。
     */
    private CertificationVO certification(UserQueryDTO row) {
        String status = row.getCertificationStatus();
        return new CertificationVO(row.getId(), row.getUserId(), status,
                row.getRejectReason(), row.getSubmittedAt(), row.getReviewedAt(),
                "UNSUBMITTED".equals(status) || "REJECTED".equals(status));
    }

    /** 转换后台审核详情并解密授权字段。 */
    private DrivingLicenseAuditDetailVO auditDetail(UserQueryDTO row) {
        return new DrivingLicenseAuditDetailVO(
                row.getId(), row.getUserId(), decrypt(row.getHolderNameCipher()), decrypt(row.getLicenseNoCipher()),
                row.getVehicleClass(), row.getFirstIssueDate(), row.getValidFrom(), row.getValidTo(),
                row.getIssuingAuthority(), row.getLicenseFrontImageKey(), row.getLicenseBackImageKey(),
                row.getRecognitionSource(), row.getCertificationStatus(), row.getRejectReason(),
                row.getSubmittedAt(), row.getReviewedAt());
    }

    private void validateDates(CertificationRequest request) {
        if (request.validFrom() != null && request.validTo() != null
                && request.validTo().isBefore(request.validFrom())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "驾驶证有效期结束日期不能早于开始日期");
        }
    }

    private String normalizeStatusFilter(String status) {
        String value = trimToEmpty(status).toUpperCase();
        if (value.isEmpty()) {
            return "";
        }
        if (!List.of("PENDING", "APPROVED", "REJECTED").contains(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "认证状态不合法");
        }
        return value;
    }

    private String normalizeAuditResult(String auditResult) {
        String value = trimToEmpty(auditResult).toUpperCase();
        if (!List.of("APPROVED", "REJECTED").contains(value)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审核结果仅支持 APPROVED 或 REJECTED");
        }
        return value;
    }

    private String normalizeRejectReason(String auditResult, String rejectReason) {
        String value = trimToEmpty(rejectReason);
        if ("REJECTED".equals(auditResult) && (value.length() < 2 || value.length() > 255)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "驳回原因长度应为 2 到 255 个字符");
        }
        return "APPROVED".equals(auditResult) ? null : value;
    }

    private String maskLicenseNo(String licenseNo) {
        String value = licenseNo.trim();
        if (value.length() <= 6) {
            return value.substring(0, 1) + "****" + value.substring(value.length() - 1);
        }
        return value.substring(0, 3) + "********" + value.substring(value.length() - 3);
    }

    /**
     * 加密敏感文本字段。
     *
     * <p>使用配置密钥派生 SHA-256 对称密钥，并使用 AES-GCM 生成随机 IV。
     * 返回值会把 IV 和密文拼接后进行 Base64 编码，便于数据库保存。</p>
     */
    private String encrypt(String value) {
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
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "敏感数据加密失败");
        }
    }

    /** 解密仅供后台授权审核详情使用的敏感字段。 */
    private String decrypt(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        try {
            byte[] key = MessageDigest.getInstance("SHA-256").digest(encryptionKey.getBytes(StandardCharsets.UTF_8));
            byte[] payload = Base64.getDecoder().decode(value);
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "敏感数据解密失败");
        }
    }

    /**
     * 从 Redis 读取缓存并反序列化。
     *
     * <p>缓存读取失败不影响主流程，直接返回 null 走数据库查询。</p>
     */
    private <T> T cacheGet(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 写入 Redis 缓存。
     *
     * <p>缓存写入失败不阻断业务返回，因为数据库仍然是最终可信数据源。</p>
     */
    private void cachePut(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // 缓存失败不影响主链路。
        }
    }

    /**
     * 合并可选字符串字段：未传入则保留旧值，传入则去除首尾空白。
     */
    private String value(String candidate, String old) {
        return candidate == null ? old : candidate.trim();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
