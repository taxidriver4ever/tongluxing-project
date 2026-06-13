package com.tongdao.auth.security;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.redis.core.script.DefaultRedisScript;
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
    private static final String PHONE_LOGIN_PREFIX = "auth:login:phone:";
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

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
        redisTemplate.opsForValue().set(phoneLoginKey(phone), token, Duration.ofSeconds(TOKEN_EXPIRE_SECONDS));

        redisTemplate.opsForHash().putAll(refreshKey(refreshToken), Map.of(
                "userId", String.valueOf(userId),
                "phone", phone,
                "token", token
        ));
        redisTemplate.expire(refreshKey(refreshToken), Duration.ofSeconds(REFRESH_TOKEN_EXPIRE_SECONDS));

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
        String currentToken = redisTemplate.opsForValue().get(phoneLoginKey(phone.toString()));
        if (!token.equals(currentToken)) {
            return Optional.empty();
        }

        return Optional.of(new AuthPrincipal(
                Long.valueOf(userId.toString()),
                phone.toString(),
                token,
                deviceId == null ? "" : deviceId.toString()
        ));
    }

    public Optional<RefreshPrincipal> resolveRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return Optional.empty();
        }
        String refreshKey = refreshKey(refreshToken);
        Object userId = redisTemplate.opsForHash().get(refreshKey, "userId");
        Object phone = redisTemplate.opsForHash().get(refreshKey, "phone");
        Object token = redisTemplate.opsForHash().get(refreshKey, "token");
        if (userId == null || phone == null || token == null) {
            return Optional.empty();
        }
        String currentToken = redisTemplate.opsForValue().get(phoneLoginKey(phone.toString()));
        if (!token.toString().equals(currentToken)) {
            return Optional.empty();
        }
        return Optional.of(new RefreshPrincipal(Long.valueOf(userId.toString()), phone.toString(), token.toString()));
    }

    public void deleteToken(AuthPrincipal principal) {
        redisTemplate.delete(tokenKey(principal.token()));
        redisTemplate.execute(COMPARE_AND_DELETE_SCRIPT, java.util.List.of(phoneLoginKey(principal.phone())), principal.token());
    }

    public void deleteRefreshToken(String refreshToken) {
        redisTemplate.delete(refreshKey(refreshToken));
    }

    private String tokenKey(String token) {
        return TOKEN_PREFIX + token;
    }

    private String refreshKey(String refreshToken) {
        return REFRESH_PREFIX + refreshToken;
    }

    private String phoneLoginKey(String phone) {
        return PHONE_LOGIN_PREFIX + phone;
    }

    public record TokenPair(String token, String refreshToken, Integer expireSeconds) {
    }

    public record RefreshPrincipal(Long userId, String phone, String token) {
    }
}
