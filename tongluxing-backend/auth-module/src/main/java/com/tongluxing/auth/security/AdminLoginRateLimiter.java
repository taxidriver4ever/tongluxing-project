package com.tongluxing.auth.security;

import java.time.Duration;
import java.util.Collections;
import java.util.Locale;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.tongluxing.auth.config.AdminAuthProperties;

import lombok.RequiredArgsConstructor;

/** Web Admin 固定窗口登录失败限流。 */
@Component
@RequiredArgsConstructor
public class AdminLoginRateLimiter {
    private static final String PREFIX = "admin:rl:login:";
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local count=redis.call('incr',KEYS[1]); "
                    + "if count==1 then redis.call('expire',KEYS[1],ARGV[1]); end; return count;",
            Long.class);

    private final StringRedisTemplate redis;
    private final AdminAuthProperties properties;

    public LimitState state(String username) {
        String value = redis.opsForValue().get(key(username));
        long count = value == null ? 0 : Long.parseLong(value);
        Long ttl = redis.getExpire(key(username));
        return new LimitState(count, Math.max(ttl == null ? 0 : ttl, 0), count >= properties.getMaxFailures());
    }

    public LimitState recordFailure(String username) {
        Long count = redis.execute(INCREMENT_SCRIPT, Collections.singletonList(key(username)),
                String.valueOf(properties.getFailureWindowSeconds()));
        Long ttl = redis.getExpire(key(username));
        long normalizedCount = count == null ? 1 : count;
        return new LimitState(normalizedCount, Math.max(ttl == null ? 0 : ttl, 0),
                normalizedCount >= properties.getMaxFailures());
    }

    public void reset(String username) {
        redis.delete(key(username));
    }

    private String key(String username) {
        return PREFIX + username.trim().toLowerCase(Locale.ROOT);
    }

    public record LimitState(long failures, long retryAfterSeconds, boolean locked) {
        public long retryAfterMinutes() {
            return Math.max(1, Duration.ofSeconds(retryAfterSeconds).toMinutes() + (retryAfterSeconds % 60 == 0 ? 0 : 1));
        }
    }
}
