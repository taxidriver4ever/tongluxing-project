package com.tongluxing.auth.security;

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.tongluxing.auth.config.AdminAuthProperties;

import lombok.RequiredArgsConstructor;

/**
 * Web Admin 固定窗口登录失败限流器。
 *
 * <p>每个规范化用户名对应一个 Redis 计数键。第一次失败通过 Lua 原子执行
 * {@code INCR + EXPIRE}，后续失败只递增，因此并发请求不会产生永不过期的计数器。
 * 达到配置上限后，在键 TTL 归零前都视为锁定。</p>
 */
@Component
@RequiredArgsConstructor
public class AdminLoginRateLimiter {
    /** Redis 键前缀；后缀为去空白并转小写的用户名。 */
    private static final String PREFIX = "admin:rl:login:";
    /** 原子递增失败次数，并仅在首次失败时设置固定窗口 TTL。 */
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local count=redis.call('incr',KEYS[1]); "
                    + "if count==1 then redis.call('expire',KEYS[1],ARGV[1]); end; return count;",
            Long.class);

    /** Redis 访问组件。 */
    private final StringRedisTemplate redis;
    /** 最大失败次数和失败窗口配置。 */
    private final AdminAuthProperties properties;

    /**
     * 读取当前失败与锁定状态，但不修改计数器。
     *
     * @param username 待检查用户名
     * @return 失败次数、剩余窗口秒数和是否锁定
     */
    public LimitState state(String username) {
        // 使用同一个规范化键读取计数和 TTL，避免大小写变化绕过限制。
        String value = redis.opsForValue().get(key(username));
        // 键不存在代表当前窗口没有失败记录。
        long count = value == null ? 0 : Long.parseLong(value);
        Long ttl = redis.getExpire(key(username));
        // Redis 对不存在或无过期键可能返回负值，对外统一截断为 0。
        return new LimitState(count, Math.max(ttl == null ? 0 : ttl, 0), count >= properties.getMaxFailures());
    }

    /**
     * 原子记录一次失败并返回记录后的状态。
     *
     * @param username 登录失败的用户名
     * @return 递增后的失败次数和剩余锁定时间
     */
    public LimitState recordFailure(String username) {
        // Lua 把 INCR 与首次 EXPIRE 放在同一个 Redis 原子操作中，避免并发产生永久计数键。
        Long count = redis.execute(INCREMENT_SCRIPT, Collections.singletonList(key(username)),
                String.valueOf(properties.getFailureWindowSeconds()));
        // 脚本完成后读取实际剩余 TTL，用于客户端重试时间提示。
        Long ttl = redis.getExpire(key(username));
        // 极少数 Redis 返回空结果时按一次失败处理，不能因基础设施异常把计数降为 0。
        long normalizedCount = count == null ? 1 : count;
        return new LimitState(normalizedCount, Math.max(ttl == null ? 0 : ttl, 0),
                normalizedCount >= properties.getMaxFailures());
    }

    /** 登录成功后删除失败窗口，使后续登录重新从零计数。 */
    public void reset(String username) {
        // 成功登录删除整个失败窗口，下一次错误重新从 1 开始计数。
        redis.delete(key(username));
    }

    /** 构造大小写无关的 Redis 键，防止通过用户名大小写绕过限制。 */
    private String key(String username) {
        // 去首尾空白并按 Locale.ROOT 转小写，避免本地语言规则影响安全键。
        return PREFIX + username.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 登录失败窗口快照。
     *
     * @param failures 当前窗口累计失败次数
     * @param retryAfterSeconds Redis 键剩余 TTL，单位秒
     * @param locked 是否达到配置的失败上限
     */
    public record LimitState(long failures, long retryAfterSeconds, boolean locked) {
        /** 将剩余秒数向上取整为至少一分钟的前端提示值。 */
        public long retryAfterMinutes() {
            // 向上取整分钟数；只要仍锁定就至少返回 1 分钟。
            return Math.max(1, Duration.ofSeconds(retryAfterSeconds).toMinutes() + (retryAfterSeconds % 60 == 0 ? 0 : 1));
        }
    }
}
