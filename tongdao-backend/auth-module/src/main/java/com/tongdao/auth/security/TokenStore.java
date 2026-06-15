package com.tongdao.auth.security;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final String ACCESS_PREFIX = "a:t:";
    private static final String REFRESH_PREFIX = "a:r:";
    private static final String PHONE_LOGIN_PREFIX = "a:u:";
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${auth.jwt.access-expire-seconds}")
    private Integer accessExpireSeconds;

    @Value("${auth.jwt.refresh-expire-seconds}")
    private Integer refreshExpireSeconds;

    public TokenPair create(Long userId, String phone, String deviceId) {
        JwtTokenProvider.JwtToken accessToken = jwtTokenProvider.createAccessToken(userId, phone, deviceId, accessExpireSeconds);
        JwtTokenProvider.JwtToken refreshToken = jwtTokenProvider.createRefreshToken(userId, phone, refreshExpireSeconds);

        String oldJti = redisTemplate.opsForValue().get(phoneLoginKey(phone));
        if (StringUtils.hasText(oldJti)) {
            redisTemplate.delete(accessKey(oldJti));
        }
        redisTemplate.opsForValue().set(accessKey(accessToken.jti()), "1", Duration.ofSeconds(accessExpireSeconds));
        redisTemplate.opsForValue().set(refreshKey(refreshToken.jti()), accessToken.jti(), Duration.ofSeconds(refreshExpireSeconds));
        redisTemplate.opsForValue().set(phoneLoginKey(phone), accessToken.jti(), Duration.ofSeconds(accessExpireSeconds));

        return new TokenPair(accessToken.token(), refreshToken.token(), accessExpireSeconds);
    }

    public Optional<AuthPrincipal> resolve(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }

        JwtTokenProvider.JwtClaims claims;
        try {
            claims = jwtTokenProvider.parseAccessToken(token);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        if (Boolean.FALSE.equals(redisTemplate.hasKey(accessKey(claims.jti())))) {
            return Optional.empty();
        }
        String currentJti = redisTemplate.opsForValue().get(phoneLoginKey(claims.phone()));
        if (!claims.jti().equals(currentJti)) {
            return Optional.empty();
        }

        return Optional.of(new AuthPrincipal(
                claims.userId(),
                claims.phone(),
                token,
                claims.deviceId()
        ));
    }

    public Optional<RefreshPrincipal> resolveRefreshToken(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            return Optional.empty();
        }
        JwtTokenProvider.JwtClaims claims;
        try {
            claims = jwtTokenProvider.parseRefreshToken(refreshToken);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }

        String accessJti = redisTemplate.opsForValue().get(refreshKey(claims.jti()));
        if (!StringUtils.hasText(accessJti)) {
            return Optional.empty();
        }
        String currentJti = redisTemplate.opsForValue().get(phoneLoginKey(claims.phone()));
        if (!accessJti.equals(currentJti)) {
            return Optional.empty();
        }
        return Optional.of(new RefreshPrincipal(claims.userId(), claims.phone(), accessJti, claims.jti()));
    }

    public void deleteToken(AuthPrincipal principal) {
        String jti = jwtTokenProvider.parseAccessToken(principal.token()).jti();
        redisTemplate.delete(accessKey(jti));
        redisTemplate.execute(COMPARE_AND_DELETE_SCRIPT, java.util.List.of(phoneLoginKey(principal.phone())), jti);
    }

    public void deleteRefreshToken(String refreshToken) {
        try {
            JwtTokenProvider.JwtClaims claims = jwtTokenProvider.parseRefreshToken(refreshToken);
            redisTemplate.delete(refreshKey(claims.jti()));
        } catch (IllegalArgumentException ignored) {
            // Invalid refresh tokens do not need Redis cleanup.
        }
    }

    private String accessKey(String jti) {
        return ACCESS_PREFIX + jti;
    }

    private String refreshKey(String jti) {
        return REFRESH_PREFIX + jti;
    }

    private String phoneLoginKey(String phone) {
        return PHONE_LOGIN_PREFIX + phone;
    }

    public record TokenPair(String token, String refreshToken, Integer expireSeconds) {
    }

    public record RefreshPrincipal(Long userId, String phone, String accessJti, String refreshJti) {
    }
}
