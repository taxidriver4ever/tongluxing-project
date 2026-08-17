package com.tongluxing.match.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 推荐候选池、短等待、旧池和最近曝光集合的统一配置。 */
@Component
@ConfigurationProperties(prefix = "recommendation.pool")
public class RecommendationPoolProperties {
    private int maxSize = 100;
    private int ttlSeconds = 180;
    private int ttlJitterSeconds = 60;
    private int staleTtlSeconds = 600;
    private int lockTtlSeconds = 30;
    private int waitTimeoutMillis = 500;
    private int waitIntervalMillis = 40;
    private int seenMaxSize = 200;
    private int seenTtlSeconds = 1_200;

    public int getMaxSize() { return maxSize; }
    public void setMaxSize(int value) { maxSize = positive(value, "max-size"); }
    public int getTtlSeconds() { return ttlSeconds; }
    public void setTtlSeconds(int value) { ttlSeconds = positive(value, "ttl-seconds"); }
    public int getTtlJitterSeconds() { return ttlJitterSeconds; }
    public void setTtlJitterSeconds(int value) {
        if (value < 0) throw new IllegalArgumentException("recommendation.pool.ttl-jitter-seconds must be non-negative");
        ttlJitterSeconds = value;
    }
    public int getStaleTtlSeconds() { return staleTtlSeconds; }
    public void setStaleTtlSeconds(int value) { staleTtlSeconds = positive(value, "stale-ttl-seconds"); }
    public int getLockTtlSeconds() { return lockTtlSeconds; }
    public void setLockTtlSeconds(int value) { lockTtlSeconds = positive(value, "lock-ttl-seconds"); }
    public int getWaitTimeoutMillis() { return waitTimeoutMillis; }
    public void setWaitTimeoutMillis(int value) { waitTimeoutMillis = positive(value, "wait-timeout-millis"); }
    public int getWaitIntervalMillis() { return waitIntervalMillis; }
    public void setWaitIntervalMillis(int value) { waitIntervalMillis = positive(value, "wait-interval-millis"); }
    public int getSeenMaxSize() { return seenMaxSize; }
    public void setSeenMaxSize(int value) { seenMaxSize = positive(value, "seen-max-size"); }
    public int getSeenTtlSeconds() { return seenTtlSeconds; }
    public void setSeenTtlSeconds(int value) { seenTtlSeconds = positive(value, "seen-ttl-seconds"); }

    private int positive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException("recommendation.pool." + name + " must be positive");
        return value;
    }
}
