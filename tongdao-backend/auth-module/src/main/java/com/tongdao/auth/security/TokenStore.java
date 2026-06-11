package com.tongdao.auth.security;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TokenStore {

    public static final int TOKEN_EXPIRE_SECONDS = 7200;
    public static final int REFRESH_TOKEN_EXPIRE_SECONDS = 604800;

    private static final String TOKEN_PREFIX = "auth:token:";
    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String USER_TOKENS_PREFIX = "auth:user:tokens:";

    private final StringRedisTemplate redisTemplate;

    public TokenPair create(Long userId, String phone, String deviceId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String refreshToken = UUID.randomUUID().toString().replace("-", "");

        String tokenKey = tokenKey(token);
        redisTemplate.opsForHash().putAll(tokenKey, Map.of(
                "userId", String.valueOf(userId),
                "phone", phone,
                "deviceId", StringUtils.hasText(deviceId) ? deviceId : ""
        ));
        redisTemplate.expire(tokenKey, Duration.ofSeconds(TOKEN_EXPIRE_SECONDS));

        redisTemplate.opsForValue().set(refreshKey(refreshToken), String.valueOf(userId), Duration.ofSeconds(REFRESH_TOKEN_EXPIRE_SECONDS));
        redisTemplate.opsForSet().add(userTokensKey(userId), token);
        redisTemplate.expire(userTokensKey(userId), Duration.ofSeconds(TOKEN_EXPIRE_SECONDS));

        return new TokenPair(token, refreshToken, TOKEN_EXPIRE_SECONDS);
    }

    public Optional<AuthPrincipal> resolve(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }

        String tokenKey = tokenKey(token);
        if (Boolean.FALSE.equals(redisTemplate.hasKey(tokenKey))) {
            return Optional.empty();
        }

        Object userId = redisTemplate.opsForHash().get(tokenKey, "userId");
        Object phone = redisTemplate.opsForHash().get(tokenKey, "phone");
        Object deviceId = redisTemplate.opsForHash().get(tokenKey, "deviceId");
        if (userId == null || phone == null) {
            return Optional.empty();
        }

        return Optional.of(new AuthPrincipal(
                Long.valueOf(userId.toString()),
                phone.toString(),
                token,
                deviceId == null ? "" : deviceId.toString()
        ));
    }

    public Optional<Long> resolveRefreshToken(String refreshToken) {
        String userId = redisTemplate.opsForValue().get(refreshKey(refreshToken));
        return StringUtils.hasText(userId) ? Optional.of(Long.valueOf(userId)) : Optional.empty();
    }

    public void deleteToken(AuthPrincipal principal) {
        redisTemplate.delete(tokenKey(principal.token()));
        redisTemplate.opsForSet().remove(userTokensKey(principal.userId()), principal.token());
    }

    public void deleteRefreshToken(String refreshToken) {
        redisTemplate.delete(refreshKey(refreshToken));
    }

    public void deleteUserTokens(Long userId) {
        String userTokensKey = userTokensKey(userId);
        Set<String> tokens = redisTemplate.opsForSet().members(userTokensKey);
        if (tokens != null && !tokens.isEmpty()) {
            redisTemplate.delete(tokens.stream().map(this::tokenKey).toList());
        }
        redisTemplate.delete(userTokensKey);
    }

    private String tokenKey(String token) {
        return TOKEN_PREFIX + token;
    }

    private String refreshKey(String refreshToken) {
        return REFRESH_PREFIX + refreshToken;
    }

    private String userTokensKey(Long userId) {
        return USER_TOKENS_PREFIX + userId;
    }

    public record TokenPair(String token, String refreshToken, Integer expireSeconds) {
    }
}
