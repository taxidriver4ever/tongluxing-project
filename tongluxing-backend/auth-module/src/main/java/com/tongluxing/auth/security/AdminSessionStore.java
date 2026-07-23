package com.tongluxing.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.auth.config.AdminAuthProperties;

import lombok.RequiredArgsConstructor;

/** 独立于用户 JWT 的 Web Admin 不透明会话存储。 */
@Component
@RequiredArgsConstructor
public class AdminSessionStore {
    private static final String SESSION_PREFIX = "admin:session:";
    private static final String CURRENT_PREFIX = "admin:current:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AdminAuthProperties properties;

    /** 创建 Admin 会话；同一管理员账号只保留最后一次登录为当前会话。 */
    public CreatedSession create(Long operatorId, String username, String displayName) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = hash(token);
        long expireAt = Instant.now().getEpochSecond() + properties.getSessionExpireSeconds();
        AdminSession session = new AdminSession(operatorId, username, displayName, expireAt);
        try {
            Duration ttl = Duration.ofSeconds(properties.getSessionExpireSeconds());
            redis.opsForValue().set(sessionKey(tokenHash), objectMapper.writeValueAsString(session), ttl);
            redis.opsForValue().set(currentKey(username), tokenHash, ttl);
        } catch (Exception exception) {
            throw new IllegalStateException("admin session create failed", exception);
        }
        return new CreatedSession(token, session, properties.getSessionExpireSeconds());
    }

    /** 解析 Admin 会话，并区分被其他登录剔除与普通无效。 */
    public AdminResolution resolveDetailed(String token) {
        if (!StringUtils.hasText(token)) return AdminResolution.invalid();
        try {
            String tokenHash = hash(token);
            String json = redis.opsForValue().get(sessionKey(tokenHash));
            if (!StringUtils.hasText(json)) return AdminResolution.invalid();
            AdminSession session = objectMapper.readValue(json, AdminSession.class);
            if (session.expireAt() <= Instant.now().getEpochSecond()) {
                redis.delete(sessionKey(tokenHash));
                return AdminResolution.invalid();
            }
            String currentHash = redis.opsForValue().get(currentKey(session.username()));
            if (StringUtils.hasText(currentHash) && !tokenHash.equals(currentHash)) {
                return AdminResolution.kicked();
            }
            if (!tokenHash.equals(currentHash)) return AdminResolution.invalid();
            return AdminResolution.valid(session);
        } catch (Exception ignored) {
            return AdminResolution.invalid();
        }
    }

    /** 兼容原有 Optional 调用。 */
    public Optional<AdminSession> resolve(String token) {
        AdminResolution resolution = resolveDetailed(token);
        return resolution.status() == AdminStatus.VALID
                ? Optional.of(resolution.session())
                : Optional.empty();
    }

    /** 删除指定 Admin 会话，不误删同账号刚建立的新会话索引。 */
    public void delete(String token) {
        if (!StringUtils.hasText(token)) return;
        try {
            String tokenHash = hash(token);
            String json = redis.opsForValue().get(sessionKey(tokenHash));
            redis.delete(sessionKey(tokenHash));
            if (!StringUtils.hasText(json)) return;
            AdminSession session = objectMapper.readValue(json, AdminSession.class);
            redis.execute(COMPARE_AND_DELETE_SCRIPT,
                    java.util.List.of(currentKey(session.username())), tokenHash);
        } catch (Exception ignored) {
            // 删除无效 token 时不影响当前管理员会话。
        }
    }

    private String sessionKey(String tokenHash) {
        return SESSION_PREFIX + tokenHash;
    }

    private String currentKey(String username) {
        return CURRENT_PREFIX + username.trim().toLowerCase();
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("admin token hash failed", exception);
        }
    }

    public enum AdminStatus {
        VALID,
        KICKED,
        INVALID
    }

    public record AdminResolution(AdminStatus status, AdminSession session) {
        public static AdminResolution valid(AdminSession session) {
            return new AdminResolution(AdminStatus.VALID, session);
        }

        public static AdminResolution kicked() {
            return new AdminResolution(AdminStatus.KICKED, null);
        }

        public static AdminResolution invalid() {
            return new AdminResolution(AdminStatus.INVALID, null);
        }
    }

    public record AdminSession(Long operatorId, String username, String displayName, Long expireAt) {
    }

    public record CreatedSession(String token, AdminSession session, Integer expireSeconds) {
    }
}
