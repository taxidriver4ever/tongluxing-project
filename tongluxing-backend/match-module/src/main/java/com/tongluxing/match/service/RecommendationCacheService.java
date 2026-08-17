package com.tongluxing.match.service;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.match.config.RecommendationCacheProperties;
import com.tongluxing.match.vo.TripRecommendPageResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 推荐缓存读写与同 key 单飞协调。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationCacheService {
    private static final String LOCK_SUFFIX = ":rebuild-lock";
    private static final String STALE_SUFFIX = ":stale";
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RecommendationCacheProperties properties;
    private final Map<String, CompletableFuture<TripRecommendPageResponse>> inFlight = new ConcurrentHashMap<>();

    public CacheResult getOrCompute(String key, Supplier<TripRecommendPageResponse> computation) {
        long readStarted = System.nanoTime();
        TripRecommendPageResponse cached = read(key);
        long initialReadMs = millis(readStarted);
        if (cached != null) return new CacheResult(cached, true, initialReadMs, 0L, false);

        CompletableFuture<TripRecommendPageResponse> mine = new CompletableFuture<>();
        CompletableFuture<TripRecommendPageResponse> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            try {
                TripRecommendPageResponse shared = existing.get(
                        properties.getLockTtlSeconds() + 1L, TimeUnit.SECONDS);
                return new CacheResult(shared, true, initialReadMs, 0L, false);
            } catch (Exception exception) {
                TripRecommendPageResponse staleAfterWait = read(key + STALE_SUFFIX);
                if (staleAfterWait != null) return new CacheResult(staleAfterWait, true, initialReadMs, 0L, false);
                log.warn("recommend_local_singleflight_wait_failed keyHash={} reason={}",
                        Integer.toHexString(key.hashCode()), exception.getClass().getSimpleName());
                return distributedCompute(key, computation, initialReadMs);
            }
        }

        try {
            CacheResult result = distributedCompute(key, computation, initialReadMs);
            mine.complete(result.value());
            return result;
        } catch (RuntimeException exception) {
            mine.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private CacheResult distributedCompute(String key, Supplier<TripRecommendPageResponse> computation,
                                           long initialReadMs) {
        String lockKey = key + LOCK_SUFFIX;
        String token = UUID.randomUUID().toString();
        boolean lockOwned = tryLock(lockKey, token);
        if (!lockOwned) {
            TripRecommendPageResponse stale = read(key + STALE_SUFFIX);
            if (stale != null) return new CacheResult(stale, true, initialReadMs, 0L, false);

            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(properties.getWaitTimeoutMillis());
            while (System.nanoTime() < deadline) {
                sleep(properties.getWaitIntervalMillis());
                TripRecommendPageResponse refreshed = read(key);
                if (refreshed != null) return new CacheResult(refreshed, true, initialReadMs, 0L, false);
                lockOwned = tryLock(lockKey, token);
                if (lockOwned) break;
            }
            if (!lockOwned) {
                TripRecommendPageResponse staleAfterDistributedWait = read(key + STALE_SUFFIX);
                if (staleAfterDistributedWait != null) {
                    return new CacheResult(staleAfterDistributedWait, true, initialReadMs, 0L, false);
                }
                long lockDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(properties.getLockTtlSeconds());
                while (System.nanoTime() < lockDeadline && !lockOwned) {
                    sleep(properties.getWaitIntervalMillis());
                    TripRecommendPageResponse refreshed = read(key);
                    if (refreshed != null) return new CacheResult(refreshed, true, initialReadMs, 0L, false);
                    lockOwned = tryLock(lockKey, token);
                }
                while (!lockOwned) {
                    TripRecommendPageResponse refreshed = read(key);
                    if (refreshed != null) return new CacheResult(refreshed, true, initialReadMs, 0L, false);
                    TripRecommendPageResponse fallback = read(key + STALE_SUFFIX);
                    if (fallback != null) return new CacheResult(fallback, true, initialReadMs, 0L, false);
                    lockOwned = tryLock(lockKey, token);
                    if (!lockOwned) sleep(properties.getWaitIntervalMillis());
                }
            }
        }

        try {
            TripRecommendPageResponse value = computation.get();
            long writeStarted = System.nanoTime();
            write(key, value);
            long writeMs = millis(writeStarted);
            return new CacheResult(value, false, initialReadMs, writeMs, true);
        } finally {
            if (lockOwned) release(lockKey, token);
        }
    }

    private TripRecommendPageResponse read(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null || json.isBlank() ? null
                    : objectMapper.readValue(json, TripRecommendPageResponse.class);
        } catch (Exception exception) {
            log.warn("recommend_cache_read_failed keyHash={} reason={}",
                    Integer.toHexString(key.hashCode()), exception.getClass().getSimpleName());
            return null;
        }
    }

    private void write(String key, TripRecommendPageResponse value) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, Duration.ofSeconds(nextTtlSeconds()));
            redisTemplate.opsForValue().set(key + STALE_SUFFIX, json,
                    Duration.ofSeconds(properties.getStaleTtlSeconds()));
        } catch (Exception exception) {
            log.warn("recommend_cache_write_failed keyHash={} reason={}",
                    Integer.toHexString(key.hashCode()), exception.getClass().getSimpleName());
        }
    }

    private boolean tryLock(String key, String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    key, token, Duration.ofSeconds(properties.getLockTtlSeconds())));
        } catch (DataAccessException exception) {
            log.warn("recommend_cache_lock_unavailable keyHash={}", Integer.toHexString(key.hashCode()));
            return true;
        }
    }

    private void release(String key, String token) {
        try {
            redisTemplate.execute(RELEASE_LOCK, java.util.List.of(key), token);
        } catch (DataAccessException exception) {
            log.warn("recommend_cache_lock_release_failed keyHash={}", Integer.toHexString(key.hashCode()));
        }
    }

    public long nextTtlSeconds() {
        int jitter = properties.getTtlJitterSeconds();
        return properties.getTtlSeconds() + (jitter == 0 ? 0 : ThreadLocalRandom.current().nextInt(jitter + 1));
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private long millis(long started) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }

    public record CacheResult(TripRecommendPageResponse value, boolean cacheHit, long cacheReadMs,
                              long cacheWriteMs, boolean computed) { }
}
