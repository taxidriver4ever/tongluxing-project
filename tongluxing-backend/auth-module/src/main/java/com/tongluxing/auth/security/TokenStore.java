package com.tongluxing.auth.security;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * Token 登录态存储。
 *
 * <p>JWT 携带身份，Redis 保存当前有效会话。App、小程序与商家 Web 统一使用
 * 账号级会话，因此同一账号后登录会立即剔除所有终端上的旧会话。</p>
 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    private static final String ACCESS_PREFIX = "a:t:";
    private static final String REFRESH_PREFIX = "a:r:";
    private static final String SESSION_PREFIX = "a:s:";
    private static final String ACCOUNT_SCOPE = "ACCOUNT";
    /** 兼容上线前签发的两个旧会话域，登录时同步覆盖才能让旧 Token 精确返回 KICKED。 */
    private static final java.util.List<String> COMPATIBLE_SCOPES =
            java.util.List.of(ACCOUNT_SCOPE, "APP_WEB", "MINI_PROGRAM");
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

    /** 创建新会话，并撤销该账号在所有客户端会话域中的旧 access token。 */
    public TokenPair create(Long userId, String phone, String deviceId, String sessionScope) {
        String normalizedDeviceId = StringUtils.hasText(deviceId) ? deviceId : "default";
        String normalizedScope = normalizeScope(sessionScope);
        JwtTokenProvider.JwtToken accessToken = jwtTokenProvider.createAccessToken(
                userId, phone, normalizedDeviceId, normalizedScope, accessExpireSeconds);
        JwtTokenProvider.JwtToken refreshToken = jwtTokenProvider.createRefreshToken(
                userId, phone, normalizedDeviceId, normalizedScope, refreshExpireSeconds);

        for (String scope : COMPATIBLE_SCOPES) {
            String oldJti = redisTemplate.opsForValue().get(sessionKey(phone, scope));
            if (StringUtils.hasText(oldJti) && !oldJti.equals(accessToken.jti())) {
                redisTemplate.delete(accessKey(oldJti));
            }
        }

        redisTemplate.opsForValue().set(accessKey(accessToken.jti()), "1", Duration.ofSeconds(accessExpireSeconds));
        redisTemplate.opsForValue().set(refreshKey(refreshToken.jti()), accessToken.jti(), Duration.ofSeconds(refreshExpireSeconds));
        for (String scope : COMPATIBLE_SCOPES) {
            redisTemplate.opsForValue().set(
                    sessionKey(phone, scope), accessToken.jti(), Duration.ofSeconds(refreshExpireSeconds));
        }
        return new TokenPair(accessToken.token(), refreshToken.token(), accessExpireSeconds);
    }

    /** 兼容旧调用；默认归入账号级单点登录会话域。 */
    public TokenPair create(Long userId, String phone, String deviceId) {
        return create(userId, phone, deviceId, ACCOUNT_SCOPE);
    }

    /** 解析 access token，并区分“被其他登录剔除”和普通无效。 */
    public AccessResolution resolveAccessToken(String token) {
        if (!StringUtils.hasText(token)) return AccessResolution.invalid();

        final JwtTokenProvider.JwtClaims claims;
        try {
            claims = jwtTokenProvider.parseAccessToken(token);
        } catch (IllegalArgumentException exception) {
            return AccessResolution.invalid();
        }

        String currentJti = redisTemplate.opsForValue().get(sessionKey(claims.phone(), claims.sessionScope()));
        if (StringUtils.hasText(currentJti) && !claims.jti().equals(currentJti)) {
            return AccessResolution.kicked();
        }
        if (!claims.jti().equals(currentJti)
                || Boolean.FALSE.equals(redisTemplate.hasKey(accessKey(claims.jti())))) {
            return AccessResolution.invalid();
        }

        return AccessResolution.valid(new AuthPrincipal(
                claims.userId(), claims.phone(), token, claims.deviceId()));
    }

    public Optional<AuthPrincipal> resolve(String token) {
        AccessResolution resolution = resolveAccessToken(token);
        return resolution.status() == AccessStatus.VALID
                ? Optional.of(resolution.principal())
                : Optional.empty();
    }

    /** 校验 refresh token，并区分旧会话被后登录剔除与普通失效。 */
    public RefreshResolution resolveRefresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) return RefreshResolution.invalid();

        final JwtTokenProvider.JwtClaims claims;
        try {
            claims = jwtTokenProvider.parseRefreshToken(refreshToken);
        } catch (IllegalArgumentException exception) {
            return RefreshResolution.invalid();
        }

        String accessJti = redisTemplate.opsForValue().get(refreshKey(claims.jti()));
        if (!StringUtils.hasText(accessJti)) return RefreshResolution.invalid();
        String currentJti = redisTemplate.opsForValue().get(sessionKey(claims.phone(), claims.sessionScope()));
        if (StringUtils.hasText(currentJti) && !accessJti.equals(currentJti)) {
            return RefreshResolution.kicked();
        }
        if (!accessJti.equals(currentJti)) return RefreshResolution.invalid();
        return RefreshResolution.valid(new RefreshPrincipal(
                claims.userId(), claims.phone(), claims.deviceId(), claims.sessionScope(), accessJti, claims.jti()));
    }

    /** 兼容原有 Optional 调用。 */
    public Optional<RefreshPrincipal> resolveRefreshToken(String refreshToken) {
        RefreshResolution resolution = resolveRefresh(refreshToken);
        return resolution.status() == RefreshStatus.VALID
                ? Optional.of(resolution.principal())
                : Optional.empty();
    }

    /** 删除当前 access token；compare-and-delete 防止旧请求误删新会话。 */
    public void deleteToken(AuthPrincipal principal) {
        JwtTokenProvider.JwtClaims claims = jwtTokenProvider.parseAccessToken(principal.token());
        redisTemplate.delete(accessKey(claims.jti()));
        for (String scope : COMPATIBLE_SCOPES) {
            redisTemplate.execute(
                    COMPARE_AND_DELETE_SCRIPT,
                    java.util.List.of(sessionKey(claims.phone(), scope)),
                    claims.jti()
            );
        }
    }

    public void deleteRefreshToken(String refreshToken) {
        try {
            JwtTokenProvider.JwtClaims claims = jwtTokenProvider.parseRefreshToken(refreshToken);
            redisTemplate.delete(refreshKey(claims.jti()));
        } catch (IllegalArgumentException ignored) {
            // 非法 refresh token 无需清理。
        }
    }

    private String accessKey(String jti) {
        return ACCESS_PREFIX + jti;
    }

    private String refreshKey(String jti) {
        return REFRESH_PREFIX + jti;
    }

    private String sessionKey(String phone, String sessionScope) {
        return SESSION_PREFIX + phone + ":" + normalizeScope(sessionScope);
    }

    private String normalizeScope(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : ACCOUNT_SCOPE;
    }

    public enum AccessStatus {
        VALID,
        KICKED,
        INVALID
    }

    public enum RefreshStatus {
        VALID,
        KICKED,
        INVALID
    }

    public record AccessResolution(AccessStatus status, AuthPrincipal principal) {
        public static AccessResolution valid(AuthPrincipal principal) {
            return new AccessResolution(AccessStatus.VALID, principal);
        }

        public static AccessResolution kicked() {
            return new AccessResolution(AccessStatus.KICKED, null);
        }

        public static AccessResolution invalid() {
            return new AccessResolution(AccessStatus.INVALID, null);
        }
    }

    public record RefreshResolution(RefreshStatus status, RefreshPrincipal principal) {
        public static RefreshResolution valid(RefreshPrincipal principal) {
            return new RefreshResolution(RefreshStatus.VALID, principal);
        }

        public static RefreshResolution kicked() {
            return new RefreshResolution(RefreshStatus.KICKED, null);
        }

        public static RefreshResolution invalid() {
            return new RefreshResolution(RefreshStatus.INVALID, null);
        }
    }

    public record TokenPair(String token, String refreshToken, Integer expireSeconds) {
    }

    public record RefreshPrincipal(
            Long userId,
            String phone,
            String deviceId,
            String sessionScope,
            String accessJti,
            String refreshJti
    ) {
    }
}
