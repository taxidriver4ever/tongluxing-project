package com.tongluxing.user.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;

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
import com.tongluxing.user.model.UserModels.PublicProfileVO;
import com.tongluxing.user.model.UserModels.UpdateUserProfileRequest;
import com.tongluxing.user.model.UserModels.UserProfileVO;
import com.tongluxing.user.service.UserService;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 用户模块业务实现。
 *
 * <p>主要负责用户资料读写、实名认证提交、公开主页隐私控制，以及 Redis 缓存维护。
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

    /**
     * 提交实名认证申请。
     *
     * <p>如果用户已有待审核或已通过的认证记录，不允许重复提交。
     * 真实姓名和证件号会加密存储，只返回认证流程状态。</p>
     */
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
        mapper.insertCertification(SnowflakeIdGenerator.nextId(), userId, encrypt(request.realName()), encrypt(request.idCardNo()),
                request.drivingLicenseImageKey().trim(), request.faceImageKey().trim(), now);
        redis.delete(java.util.List.of(PROFILE_CACHE.formatted(userId), PUBLIC_CACHE.formatted(userId)));
        return certification(mapper.findLatestCertification(userId));
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
                profile.cityName(), profile.bio(), profile.certificationStatus());
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
     * 将实名认证查询结果转换为接口返回对象。
     */
    private CertificationVO certification(UserQueryDTO row) {
        return new CertificationVO(row.getId(), row.getUserId(), row.getCertificationStatus(),
                row.getRejectReason(), row.getSubmittedAt(), row.getReviewedAt());
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
}
