package com.tongdao.auth.security;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * Token 登录态存储。
 *
 * <p>JWT 负责携带用户身份，Redis 负责保存“当前有效令牌”和刷新令牌状态，从而支持退出登录、单手机号单点登录和刷新令牌失效。</p>
 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    /** access token 的 Redis key 前缀，后接 JWT jti。 */
    private static final String ACCESS_PREFIX = "a:t:";
    /** refresh token 的 Redis key 前缀，后接 refresh token 的 jti。 */
    private static final String REFRESH_PREFIX = "a:r:";
    /** 手机号当前登录 access jti 的 Redis key 前缀，用于控制同一手机号只保留最新登录态。 */
    private static final String PHONE_LOGIN_PREFIX = "a:u:";
    /** 比较并删除脚本，避免退出登录时误删新登录产生的 jti。 */
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    /** Redis 字符串操作模板。 */
    private final StringRedisTemplate redisTemplate;
    /** JWT 签发与解析组件。 */
    private final JwtTokenProvider jwtTokenProvider;

    /** access token 有效期，单位秒。 */
    @Value("${auth.jwt.access-expire-seconds}")
    private Integer accessExpireSeconds;

    /** refresh token 有效期，单位秒。 */
    @Value("${auth.jwt.refresh-expire-seconds}")
    private Integer refreshExpireSeconds;

    /**
     * 创建一组 access token 和 refresh token，并写入 Redis 登录态。
     *
     * <p>如果同一手机号已有登录态，会删除旧 access token，使旧设备立即失效。</p>
     */
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

    /**
     * 解析 access token 并校验 Redis 登录态。
     *
     * @return 校验通过时返回当前登录主体，否则返回空
     */
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

    /**
     * 解析 refresh token 并校验其是否仍绑定当前有效 access token。
     */
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

    /**
     * 删除当前 access token 登录态。
     *
     * <p>手机号登录态使用 compare-and-delete，避免用户刚重新登录后旧请求退出把新登录态删掉。</p>
     */
    public void deleteToken(AuthPrincipal principal) {
        String jti = jwtTokenProvider.parseAccessToken(principal.token()).jti();
        redisTemplate.delete(accessKey(jti));
        redisTemplate.execute(COMPARE_AND_DELETE_SCRIPT, java.util.List.of(phoneLoginKey(principal.phone())), jti);
    }

    /** 删除 refresh token；非法 token 直接忽略。 */
    public void deleteRefreshToken(String refreshToken) {
        try {
            JwtTokenProvider.JwtClaims claims = jwtTokenProvider.parseRefreshToken(refreshToken);
            redisTemplate.delete(refreshKey(claims.jti()));
        } catch (IllegalArgumentException ignored) {
            // Invalid refresh tokens do not need Redis cleanup.
        }
    }

    /** 拼接 access token Redis key。 */
    private String accessKey(String jti) {
        return ACCESS_PREFIX + jti;
    }

    /** 拼接 refresh token Redis key。 */
    private String refreshKey(String jti) {
        return REFRESH_PREFIX + jti;
    }

    /** 拼接手机号当前登录态 Redis key。 */
    private String phoneLoginKey(String phone) {
        return PHONE_LOGIN_PREFIX + phone;
    }

    /** Token 签发结果。 */
    public record TokenPair(String token, String refreshToken, Integer expireSeconds) {
    }

    /** refresh token 校验通过后的主体信息。 */
    public record RefreshPrincipal(Long userId, String phone, String accessJti, String refreshJti) {
    }
}
