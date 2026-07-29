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

/**
 * 独立于普通用户 JWT 的 Web Admin 不透明会话存储。
 *
 * <p>客户端拿到 32 字节安全随机数的 Base64URL 文本，Redis 只保存其 SHA-256 摘要，
 * 即使 Redis 内容泄露也不能直接作为 Bearer Token 使用。每个用户名另有一个“当前摘要”
 * 索引，用于实现同一后台账号只保留最后一次登录。</p>
 *
 * <p>Redis 数据关系：</p>
 * <ul>
 *   <li>{@code admin:session:<tokenHash>}：序列化后的操作员会话，TTL 为会话有效期；</li>
 *   <li>{@code admin:current:<username>}：该账号当前 tokenHash，用于识别旧会话被顶下线。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AdminSessionStore {
    /** 按 Token 摘要保存完整会话 JSON 的 Redis 键前缀。 */
    private static final String SESSION_PREFIX = "admin:session:";
    /** 按用户名保存当前 Token 摘要的 Redis 键前缀。 */
    private static final String CURRENT_PREFIX = "admin:current:";
    /** 密码学安全随机源；单例复用避免重复初始化系统熵池。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    /** 仅当当前索引仍指向待删除 Token 时才删除，避免并发退出误删新登录。 */
    private static final DefaultRedisScript<Long> COMPARE_AND_DELETE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
    );

    /** Redis 字符串读写组件。 */
    private final StringRedisTemplate redis;
    /** AdminSession 与 Redis JSON 之间的序列化器。 */
    private final ObjectMapper objectMapper;
    /** 会话 TTL 等后台认证配置。 */
    private final AdminAuthProperties properties;

    /**
     * 创建 Admin 会话；同一管理员账号只保留最后一次登录为当前会话。
     *
     * <p>先生成高熵随机 Token，再将摘要作为 Redis 键。旧会话数据无需立即扫描删除：
     * current 索引覆盖后，旧 Token 会被精确识别为 KICKED，并在自身 TTL 到期后自动清理。</p>
     *
     * @return 包含明文 Token、会话快照和有效期的创建结果
     */
    public CreatedSession create(Long operatorId, String username, String displayName) {
        // 使用 32 字节密码学安全随机数，提供 256 位会话熵，避免令牌可预测。
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        // Base64URL 去除 padding，便于直接放入 HTTP Header 且不需要额外转义。
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // Redis 仅持有摘要；客户端明文 Token 即使 Redis 泄露也不能被直接恢复。
        String tokenHash = hash(token);
        // 除 Redis TTL 外保存绝对过期时间，让解析与响应都能明确判断会话期限。
        long expireAt = Instant.now().getEpochSecond() + properties.getSessionExpireSeconds();
        AdminSession session = new AdminSession(operatorId, username, displayName, expireAt);
        try {
            Duration ttl = Duration.ofSeconds(properties.getSessionExpireSeconds());
            // 会话键保存操作员快照，供后续 /me 与权限主体构建使用。
            redis.opsForValue().set(sessionKey(tokenHash), objectMapper.writeValueAsString(session), ttl);
            // current 索引覆盖旧摘要，实现同一后台用户名只保留最后一次登录。
            redis.opsForValue().set(currentKey(username), tokenHash, ttl);
        } catch (Exception exception) {
            // 会话创建失败不能向客户端返回 Token，否则会生成无法验证的幽灵会话。
            throw new IllegalStateException("admin session create failed", exception);
        }
        // 明文 Token 只通过本次返回值交给 Controller，后续服务端都使用摘要。
        return new CreatedSession(token, session, properties.getSessionExpireSeconds());
    }

    /**
     * 解析 Admin 会话，并区分被其他登录剔除与普通无效。
     *
     * <p>只有会话键存在、JSON 可解析、业务过期时间未到且 current 索引仍指向本摘要时才有效。
     * current 指向其他摘要返回 KICKED，便于前端展示“账号在其他设备登录”。其余异常统一降级为 INVALID，
     * 避免把 Redis、JSON 或摘要细节暴露给客户端。</p>
     */
    public AdminResolution resolveDetailed(String token) {
        // 公开接口或未登录请求可能没有 Token，直接按普通无效处理。
        if (!StringUtils.hasText(token)) return AdminResolution.invalid();
        try {
            // 客户端 Token 先做相同 SHA-256 摘要，再访问 Redis，明文从不作为键。
            String tokenHash = hash(token);
            String json = redis.opsForValue().get(sessionKey(tokenHash));
            // 会话键不存在通常表示自然过期、主动退出或伪造 Token。
            if (!StringUtils.hasText(json)) return AdminResolution.invalid();
            AdminSession session = objectMapper.readValue(json, AdminSession.class);
            if (session.expireAt() <= Instant.now().getEpochSecond()) {
                // 双重检查绝对过期时间，并主动清理可能因 TTL 偏差残留的会话键。
                redis.delete(sessionKey(tokenHash));
                return AdminResolution.invalid();
            }
            // current 索引决定本 Token 是否仍是该用户名最后一次登录。
            String currentHash = redis.opsForValue().get(currentKey(session.username()));
            if (StringUtils.hasText(currentHash) && !tokenHash.equals(currentHash)) {
                // 会话数据仍存在但 current 已改变，精确判定为被新登录顶下线。
                return AdminResolution.kicked();
            }
            // current 缺失时无法证明该会话仍有效，按普通失效处理。
            if (!tokenHash.equals(currentHash)) return AdminResolution.invalid();
            return AdminResolution.valid(session);
        } catch (Exception ignored) {
            // Redis、JSON 或摘要异常统一隐藏为无效会话，避免暴露内部故障细节。
            return AdminResolution.invalid();
        }
    }

    /** 兼容只关心“有效/无效”的调用方；KICKED 与 INVALID 都映射为空 Optional。 */
    public Optional<AdminSession> resolve(String token) {
        // 兼容旧接口：只有 VALID 返回会话，KICKED 与 INVALID 都映射为空。
        AdminResolution resolution = resolveDetailed(token);
        return resolution.status() == AdminStatus.VALID
                ? Optional.of(resolution.session())
                : Optional.empty();
    }

    /**
     * 删除指定 Admin 会话，不误删同账号刚建立的新会话索引。
     *
     * <p>先读取会话以取得用户名，再删除会话键，最后通过 Lua compare-and-delete 清理当前索引。
     * 对空 Token、过期 Token 和解析失败采用幂等忽略策略。</p>
     */
    public void delete(String token) {
        // 退出接口保持幂等：空 Token 不访问 Redis，也不影响其他会话。
        if (!StringUtils.hasText(token)) return;
        try {
            String tokenHash = hash(token);
            // 删除前先读取会话 JSON，因为用户名只保存在会话快照中。
            String json = redis.opsForValue().get(sessionKey(tokenHash));
            // 会话键优先删除，保证本 Token 立即无法再次认证。
            redis.delete(sessionKey(tokenHash));
            // 已过期或已退出的 Token 没有会话数据，无需再处理 current 索引。
            if (!StringUtils.hasText(json)) return;
            AdminSession session = objectMapper.readValue(json, AdminSession.class);
            // 仅当 current 仍等于本摘要时删除，避免旧页面退出误删新登录索引。
            redis.execute(COMPARE_AND_DELETE_SCRIPT,
                    java.util.List.of(currentKey(session.username())), tokenHash);
        } catch (Exception ignored) {
            // 删除无效 token 时不影响当前管理员会话。
        }
    }

    /** 构造按 Token 摘要索引的会话键。 */
    private String sessionKey(String tokenHash) {
        // tokenHash 是固定长度十六进制文本，可安全拼接为 Redis 键。
        return SESSION_PREFIX + tokenHash;
    }

    /** 构造大小写无关的账号当前会话索引键。 */
    private String currentKey(String username) {
        // 用户名去空白并转小写，确保大小写变化不能产生多个并行会话索引。
        return CURRENT_PREFIX + username.trim().toLowerCase();
    }

    /**
     * 对客户端明文 Token 做 SHA-256 摘要。
     *
     * @return 64 位小写十六进制摘要
     */
    private String hash(String token) {
        try {
            // SHA-256 是单向摘要；这里用于降低 Redis 会话数据泄露后的 Token 可用性。
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            // 使用固定 64 位小写十六进制作为 Redis 键后缀。
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            // 运行环境缺失 SHA-256 属于不可恢复的基础设施错误，不能降级保存明文。
            throw new IllegalStateException("admin token hash failed", exception);
        }
    }

    /** Admin 会话解析结果类型。 */
    public enum AdminStatus {
        /** Token 是该账号当前且未过期的会话。 */
        VALID,
        /** 会话本身存在，但账号 current 索引已被后一次登录覆盖。 */
        KICKED,
        /** Token 缺失、过期、损坏或不属于当前账号会话。 */
        INVALID
    }

    /** 详细解析结果；非 VALID 状态下 session 固定为空。 */
    public record AdminResolution(AdminStatus status, AdminSession session) {
        public static AdminResolution valid(AdminSession session) {
            // 只有有效状态携带会话快照。
            return new AdminResolution(AdminStatus.VALID, session);
        }

        public static AdminResolution kicked() {
            // 被顶下线时不暴露旧操作员信息，调用方只能读取状态。
            return new AdminResolution(AdminStatus.KICKED, null);
        }

        public static AdminResolution invalid() {
            // 过期、退出、伪造和内部解析失败统一映射为 INVALID。
            return new AdminResolution(AdminStatus.INVALID, null);
        }
    }

    /**
     * Redis 中保存的 Admin 会话快照。
     *
     * @param operatorId 后台操作员 ID
     * @param username 登录名
     * @param displayName 展示名称
     * @param expireAt 绝对过期 Unix 秒
     */
    public record AdminSession(Long operatorId, String username, String displayName, Long expireAt) {
    }

    /**
     * 新建会话结果。
     *
     * @param token 仅返回给客户端的明文随机 Token
     * @param session Redis 中保存的会话快照
     * @param expireSeconds 有效期秒数
     */
    public record CreatedSession(String token, AdminSession session, Integer expireSeconds) {
    }
}
