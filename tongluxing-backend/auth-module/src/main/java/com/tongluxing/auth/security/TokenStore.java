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
 *
 * <p>三类 Redis 键共同完成可撤销 JWT：</p>
 * <ul>
 *   <li>{@code a:t:<access-jti>}：access token 仍有效的存在标记；</li>
 *   <li>{@code a:r:<refresh-jti>}：refresh token 对应的 access JTI；</li>
 *   <li>{@code a:s:<phone>:<scope>}：账号当前会话的 access JTI。</li>
 * </ul>
 * <p>JWT 验签只能证明令牌由服务端签发且未自然过期，只有以上 Redis 状态同时匹配，
 * 才表示令牌尚未退出、刷新或被其他设备登录顶替。</p>
 */
@Component
@RequiredArgsConstructor
public class TokenStore {

    /** access token 有效标记键前缀，后缀是 access JTI。 */
    private static final String ACCESS_PREFIX = "a:t:";
    /** refresh token 到 access JTI 映射键前缀，后缀是 refresh JTI。 */
    private static final String REFRESH_PREFIX = "a:r:";
    /** 账号当前会话索引键前缀，后缀是手机号和会话域。 */
    private static final String SESSION_PREFIX = "a:s:";
    /** 当前统一的账号级单点登录会话域。 */
    private static final String ACCOUNT_SCOPE = "ACCOUNT";
    /** 兼容上线前签发的两个旧会话域，登录时同步覆盖才能让旧 Token 精确返回 KICKED。 */
    private static final java.util.List<String> COMPATIBLE_SCOPES =
            java.util.List.of(ACCOUNT_SCOPE, "APP_WEB", "MINI_PROGRAM");
    /** 仅在索引值仍等于旧 JTI 时删除，避免迟到的退出请求误删新会话。 */
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    /** Redis 登录态读写组件。 */
    private final StringRedisTemplate redisTemplate;
    /** JWT 签发、验签和声明解析组件。 */
    private final JwtTokenProvider jwtTokenProvider;

    /** access token Redis 标记与 JWT 的有效期，单位秒。 */
    @Value("${auth.jwt.access-expire-seconds}")
    private Integer accessExpireSeconds;

    /** refresh 映射和账号当前会话索引的有效期，单位秒。 */
    @Value("${auth.jwt.refresh-expire-seconds}")
    private Integer refreshExpireSeconds;

    /**
     * 创建一对新令牌，并撤销该账号所有兼容会话域中的旧 access token。
     *
     * <p>先签发新的 access/refresh JWT，再读取旧 current JTI 并删除其 access 标记，
     * 最后写入新令牌标记与全部兼容 current 索引。这样新登录可立即使历史 Token 返回 KICKED。</p>
     *
     * @param userId 全局业务用户 ID
     * @param phone 登录手机号，也是账号会话索引的一部分
     * @param deviceId 设备标识；空值规范化为 default
     * @param sessionScope 调用方会话域；当前最终统一为账号级
     * @return access token、refresh token 与 access 有效期
     */
    public TokenPair create(Long userId, String phone, String deviceId, String sessionScope) {
        // 旧客户端可能不上传设备号或会话域；先规范化，确保 JWT 声明与 Redis 键维度一致。
        String normalizedDeviceId = StringUtils.hasText(deviceId) ? deviceId : "default";
        String normalizedScope = normalizeScope(sessionScope);

        // access 和 refresh 分别生成独立 JTI；令牌类型会写入 JWT，二者不能互换。
        JwtTokenProvider.JwtToken accessToken = jwtTokenProvider.createAccessToken(
                userId, phone, normalizedDeviceId, normalizedScope, accessExpireSeconds);
        JwtTokenProvider.JwtToken refreshToken = jwtTokenProvider.createRefreshToken(
                userId, phone, normalizedDeviceId, normalizedScope, refreshExpireSeconds);

        // 查找当前及历史会话域的旧 JTI，保证升级后仍能顶下旧版本客户端。
        for (String scope : COMPATIBLE_SCOPES) {
            String oldJti = redisTemplate.opsForValue().get(sessionKey(phone, scope));
            if (StringUtils.hasText(oldJti) && !oldJti.equals(accessToken.jti())) {
                // 删除旧 access 标记后，旧 JWT 即使尚未自然过期也无法通过 Redis 校验。
                redisTemplate.delete(accessKey(oldJti));
            }
        }

        // access 标记负责主动撤销；TTL 与 access JWT 的 exp 对齐。
        redisTemplate.opsForValue().set(accessKey(accessToken.jti()), "1", Duration.ofSeconds(accessExpireSeconds));
        // refresh 映射把 refresh JTI 关联到本轮 access JTI，便于校验与一次性消费。
        redisTemplate.opsForValue().set(refreshKey(refreshToken.jti()), accessToken.jti(), Duration.ofSeconds(refreshExpireSeconds));
        for (String scope : COMPATIBLE_SCOPES) {
            // 所有兼容 current 索引同时指向新 JTI，使历史域令牌被精确识别为 KICKED。
            redisTemplate.opsForValue().set(
                    sessionKey(phone, scope), accessToken.jti(), Duration.ofSeconds(refreshExpireSeconds));
        }
        // JTI 关联只留在服务端，对客户端返回令牌文本与 access 有效期。
        return new TokenPair(accessToken.token(), refreshToken.token(), accessExpireSeconds);
    }

    /** 兼容旧调用；默认归入账号级单点登录会话域。 */
    public TokenPair create(Long userId, String phone, String deviceId) {
        // 未显式指定 scope 的旧调用方也必须加入账号级单点登录域。
        return create(userId, phone, deviceId, ACCOUNT_SCOPE);
    }

    /** 解析 access token，并区分“被其他登录剔除”和普通无效。 */
    public AccessResolution resolveAccessToken(String token) {
        // 空 Token 常见于公开接口或未登录请求，直接按普通无效处理。
        if (!StringUtils.hasText(token)) return AccessResolution.invalid();

        final JwtTokenProvider.JwtClaims claims;
        try {
            // JWT 层依次验证格式、签名、issuer、exp 和 access 类型。
            claims = jwtTokenProvider.parseAccessToken(token);
        } catch (IllegalArgumentException exception) {
            // 不向上层暴露具体失败阶段，避免泄露签名与声明校验细节。
            return AccessResolution.invalid();
        }

        // current JTI 表示该手机号在本会话域最后一次成功登录签发的 access token。
        String currentJti = redisTemplate.opsForValue().get(sessionKey(claims.phone(), claims.sessionScope()));
        if (StringUtils.hasText(currentJti) && !claims.jti().equals(currentJti)) {
            // 索引存在但指向其他 JTI，说明账号后来在其他终端重新登录。
            return AccessResolution.kicked();
        }
        if (!claims.jti().equals(currentJti)
                || Boolean.FALSE.equals(redisTemplate.hasKey(accessKey(claims.jti())))) {
            // current 缺失/不匹配或 access 标记被退出删除，都属于普通失效。
            return AccessResolution.invalid();
        }

        // JWT 与 Redis 双重状态都有效后，才构造可写入 SecurityContext 的可信主体。
        return AccessResolution.valid(new AuthPrincipal(
                claims.userId(), claims.phone(), token, claims.deviceId()));
    }

    public Optional<AuthPrincipal> resolve(String token) {
        // 兼容旧接口：只返回有效主体，把 KICKED 与 INVALID 都折叠为空。
        AccessResolution resolution = resolveAccessToken(token);
        return resolution.status() == AccessStatus.VALID
                ? Optional.of(resolution.principal())
                : Optional.empty();
    }

    /** 校验 refresh token，并区分旧会话被后登录剔除与普通失效。 */
    public RefreshResolution resolveRefresh(String refreshToken) {
        // 刷新令牌为空时不进入 JWT 解析，直接要求客户端重新登录。
        if (!StringUtils.hasText(refreshToken)) return RefreshResolution.invalid();

        final JwtTokenProvider.JwtClaims claims;
        try {
            // 除通用验签外还强制 t=r，防止 access token 被用于刷新接口。
            claims = jwtTokenProvider.parseRefreshToken(refreshToken);
        } catch (IllegalArgumentException exception) {
            return RefreshResolution.invalid();
        }

        // refresh JTI 必须仍有映射；删除该键即可实现 refresh token 一次性使用。
        String accessJti = redisTemplate.opsForValue().get(refreshKey(claims.jti()));
        if (!StringUtils.hasText(accessJti)) return RefreshResolution.invalid();
        // current JTI 用于确认 refresh 所属会话仍是账号最后一次登录。
        String currentJti = redisTemplate.opsForValue().get(sessionKey(claims.phone(), claims.sessionScope()));
        if (StringUtils.hasText(currentJti) && !accessJti.equals(currentJti)) {
            // refresh 映射存在但 current 已变化，说明它来自被新登录顶替的旧会话。
            return RefreshResolution.kicked();
        }
        // current 索引缺失时无法证明会话仍有效，按普通失效处理。
        if (!accessJti.equals(currentJti)) return RefreshResolution.invalid();
        // 只返回重新签发所需的可信身份和 JTI 关系。
        return RefreshResolution.valid(new RefreshPrincipal(
                claims.userId(), claims.phone(), claims.deviceId(), claims.sessionScope(), accessJti, claims.jti()));
    }

    /** 兼容原有 Optional 调用。 */
    public Optional<RefreshPrincipal> resolveRefreshToken(String refreshToken) {
        // 旧调用方只关心能否刷新，因此折叠详细失败状态。
        RefreshResolution resolution = resolveRefresh(refreshToken);
        return resolution.status() == RefreshStatus.VALID
                ? Optional.of(resolution.principal())
                : Optional.empty();
    }

    /** 删除当前 access token；compare-and-delete 防止旧请求误删新会话。 */
    public void deleteToken(AuthPrincipal principal) {
        // principal 保存当前原始 access token，重新解析可取得可信 JTI、手机号与会话域。
        JwtTokenProvider.JwtClaims claims = jwtTokenProvider.parseAccessToken(principal.token());
        // 先删除 access 存在标记，使当前 Token 从下一次请求开始立即失效。
        redisTemplate.delete(accessKey(claims.jti()));
        for (String scope : COMPATIBLE_SCOPES) {
            // 只有索引仍指向当前 JTI 才删除，避免迟到的退出请求误删新登录会话。
            redisTemplate.execute(
                    COMPARE_AND_DELETE_SCRIPT,
                    java.util.List.of(sessionKey(claims.phone(), scope)),
                    claims.jti()
            );
        }
    }

    public void deleteRefreshToken(String refreshToken) {
        try {
            // 合法 refresh token 可通过自身 JTI 精确删除映射，阻止二次刷新。
            JwtTokenProvider.JwtClaims claims = jwtTokenProvider.parseRefreshToken(refreshToken);
            redisTemplate.delete(refreshKey(claims.jti()));
        } catch (IllegalArgumentException ignored) {
            // 非法 refresh token 无需清理。
        }
    }

    /** 将 access JTI 转换为 Redis 有效标记键。 */
    private String accessKey(String jti) {
        // JTI 由服务端生成，不包含用户可控的 Redis 分隔符。
        return ACCESS_PREFIX + jti;
    }

    /** 将 refresh JTI 转换为 Redis 映射键。 */
    private String refreshKey(String jti) {
        // refresh 与 access 分离前缀，避免偶然相同 JTI 产生类型碰撞。
        return REFRESH_PREFIX + jti;
    }

    /** 构造账号当前会话索引键。 */
    private String sessionKey(String phone, String sessionScope) {
        // 手机号定位账号，规范化 scope 用于兼容历史多端会话设计。
        return SESSION_PREFIX + phone + ":" + normalizeScope(sessionScope);
    }

    /** 将空会话域归入 ACCOUNT，其他值去空白并转大写。 */
    private String normalizeScope(String value) {
        // 空值落入 ACCOUNT；其他值去空白并转大写，保证 Redis 键稳定。
        return StringUtils.hasText(value) ? value.trim().toUpperCase() : ACCOUNT_SCOPE;
    }

    /** access token 的详细解析状态。 */
    public enum AccessStatus {
        /** JWT 与 Redis 当前会话完全匹配。 */
        VALID,
        /** JWT 合法但账号已被后一次登录切换到其他 JTI。 */
        KICKED,
        /** JWT 或 Redis 状态缺失、过期、损坏或不匹配。 */
        INVALID
    }

    /** refresh token 的详细解析状态。 */
    public enum RefreshStatus {
        VALID,
        KICKED,
        INVALID
    }

    /** access token 解析结果；只有 VALID 状态携带 principal。 */
    public record AccessResolution(AccessStatus status, AuthPrincipal principal) {
        public static AccessResolution valid(AuthPrincipal principal) {
            // VALID 必须携带主体，供过滤器创建 Authentication。
            return new AccessResolution(AccessStatus.VALID, principal);
        }

        public static AccessResolution kicked() {
            // 非有效状态不携带主体，避免调用方继续使用旧身份。
            return new AccessResolution(AccessStatus.KICKED, null);
        }

        public static AccessResolution invalid() {
            // 过期、损坏、退出或状态缺失统一建模为 INVALID。
            return new AccessResolution(AccessStatus.INVALID, null);
        }
    }

    /** refresh token 解析结果；只有 VALID 状态携带 principal。 */
    public record RefreshResolution(RefreshStatus status, RefreshPrincipal principal) {
        public static RefreshResolution valid(RefreshPrincipal principal) {
            // 完整通过 JWT、refresh 映射和 current 索引校验才返回主体。
            return new RefreshResolution(RefreshStatus.VALID, principal);
        }

        public static RefreshResolution kicked() {
            // 被顶下线只保留状态，不向业务层暴露旧会话身份。
            return new RefreshResolution(RefreshStatus.KICKED, null);
        }

        public static RefreshResolution invalid() {
            // 过期、损坏、已消费或状态缺失统一返回 INVALID。
            return new RefreshResolution(RefreshStatus.INVALID, null);
        }
    }

    /**
     * 登录或刷新后返回的令牌对。
     *
     * @param token access token
     * @param refreshToken 与其配对的 refresh token
     * @param expireSeconds access token 有效期秒数
     */
    public record TokenPair(String token, String refreshToken, Integer expireSeconds) {
    }

    /**
     * 刷新流程需要的可信身份和令牌关联。
     *
     * @param accessJti 该 refresh token 当前绑定的 access JTI
     * @param refreshJti refresh token 自身 JTI
     */
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
