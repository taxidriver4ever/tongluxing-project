package com.tongluxing.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.springframework.data.redis.core.StringRedisTemplate;
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
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final AdminAuthProperties properties;

    /** 创建 Admin 会话；Redis Key 只保存 token 的 SHA-256。 */
    public CreatedSession create(Long operatorId, String username, String displayName) {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        long expireAt = Instant.now().getEpochSecond() + properties.getSessionExpireSeconds();
        AdminSession session = new AdminSession(operatorId, username, displayName, expireAt);
        try {
            redis.opsForValue().set(key(token), objectMapper.writeValueAsString(session),
                    Duration.ofSeconds(properties.getSessionExpireSeconds()));
        } catch (Exception exception) {
            throw new IllegalStateException("admin session create failed", exception);
        }
        return new CreatedSession(token, session, properties.getSessionExpireSeconds());
    }

    /** 解析并校验 Admin 会话。 */
    public Optional<AdminSession> resolve(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key(token));
            if (!StringUtils.hasText(json)) {
                return Optional.empty();
            }
            AdminSession session = objectMapper.readValue(json, AdminSession.class);
            if (session.expireAt() <= Instant.now().getEpochSecond()) {
                redis.delete(key(token));
                return Optional.empty();
            }
            return Optional.of(session);
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    /** 删除指定 Admin 会话。 */
    public void delete(String token) {
        if (StringUtils.hasText(token)) {
            redis.delete(key(token));
        }
    }

    private String key(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return SESSION_PREFIX + java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("admin token hash failed", exception);
        }
    }

    public record AdminSession(Long operatorId, String username, String displayName, Long expireAt) {
    }

    public record CreatedSession(String token, AdminSession session, Integer expireSeconds) {
    }
}
