package com.tongluxing.match.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 推荐缓存、防击穿锁和旧值兜底的统一配置。 */
@Component
@ConfigurationProperties(prefix = "recommendation.cache")
public class RecommendationCacheProperties {
    private int ttlSeconds = 60;
    private int ttlJitterSeconds = 30;
    private int lockTtlSeconds = 20;
    private int waitTimeoutMillis = 5_000;
    private int waitIntervalMillis = 50;
    private int staleTtlSeconds = 300;

    public int getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(int value) { ttlSeconds = positive(value, "ttl-seconds"); }
    public int getTtlJitterSeconds() { return ttlJitterSeconds; }
    public void setTtlJitterSeconds(int value) {
        if (value < 0) throw new IllegalArgumentException("recommendation.cache.ttl-jitter-seconds must be non-negative");
        ttlJitterSeconds = value;
    }
    public int getLockTtlSeconds() { return lockTtlSeconds; }
    public void setLockTtlSeconds(int value) { lockTtlSeconds = positive(value, "lock-ttl-seconds"); }
    public int getWaitTimeoutMillis() { return waitTimeoutMillis; }
    public void setWaitTimeoutMillis(int value) { waitTimeoutMillis = positive(value, "wait-timeout-millis"); }
    public int getWaitIntervalMillis() { return waitIntervalMillis; }
    public void setWaitIntervalMillis(int value) { waitIntervalMillis = positive(value, "wait-interval-millis"); }
    public int getStaleTtlSeconds() { return staleTtlSeconds; }
    public void setStaleTtlSeconds(int value) { staleTtlSeconds = positive(value, "stale-ttl-seconds"); }

    private int positive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException("recommendation.cache." + name + " must be positive");
        return value;
    }
}

