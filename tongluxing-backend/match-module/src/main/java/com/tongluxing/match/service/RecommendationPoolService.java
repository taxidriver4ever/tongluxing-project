package com.tongluxing.match.service;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.tongluxing.match.config.RecommendationPoolProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 推荐池的单飞构建、原子游标消费、stale 兜底和最近曝光维护。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationPoolService {
    private static final String POOL_PREFIX = "recommend:pool:";
    private static final String CURSOR_SUFFIX = ":cursor";
    private static final String LOCK_SUFFIX = ":rebuild-lock";
    private static final String STALE_SUFFIX = ":stale";
    private static final String SEEN_PREFIX = "recommend:seen:";
    private static final String VERSION_KEY = "recommend:pool:version";
    private static final DefaultRedisScript<Long> RELEASE_LOCK = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
            Long.class);
    private static final DefaultRedisScript<Long> WRITE_POOL = new DefaultRedisScript<>(
            "redis.call('set',KEYS[1],ARGV[1],'EX',ARGV[2]); "
                    + "redis.call('set',KEYS[2],ARGV[1],'EX',ARGV[3]); "
                    + "redis.call('del',KEYS[3]); return 1",
            Long.class);
    private static final DefaultRedisScript<Long> INVALIDATE_CURRENT = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1])==ARGV[1] then "
                    + "redis.call('del',KEYS[1]); redis.call('del',KEYS[2]); return 1 else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RecommendationPoolProperties properties;
    private final Map<String, CompletableFuture<RecommendationPool>> inFlight = new ConcurrentHashMap<>();

    public String poolKey(Long userId, String reference, String sort, String location) {
        return POOL_PREFIX + userId + ":" + reference + ":" + sort + ":" + location + ":v" + currentVersion();
    }

    public PoolLoadResult getOrBuild(String key, Supplier<RecommendationPool> builder) {
        long readStarted = System.nanoTime();
        RecommendationPool cached = read(key);
        long readMs = millis(readStarted);
        if (cached != null) return new PoolLoadResult(cached, key, true, false, false, readMs, 0L);

        CompletableFuture<RecommendationPool> mine = new CompletableFuture<>();
        CompletableFuture<RecommendationPool> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            try {
                RecommendationPool shared = existing.get(properties.getWaitTimeoutMillis(), TimeUnit.MILLISECONDS);
                return new PoolLoadResult(shared, key, true, false, false, readMs, 0L);
            } catch (Exception exception) {
                RecommendationPool fresh = read(key);
                if (fresh != null) return new PoolLoadResult(fresh, key, true, false, false, readMs, 0L);
                RecommendationPool stale = read(key + STALE_SUFFIX);
                if (stale != null) return new PoolLoadResult(stale, key + STALE_SUFFIX, true, true, false, readMs, 0L);
                log.warn("recommend_pool_local_wait_timeout keyHash={} waitMs={}",
                        Integer.toHexString(key.hashCode()), properties.getWaitTimeoutMillis());
                return PoolLoadResult.unavailable(readMs);
            }
        }

        try {
            PoolLoadResult result = distributedBuild(key, builder, readMs);
            if (result.pool() != null) mine.complete(result.pool());
            else mine.completeExceptionally(new IllegalStateException("recommendation pool unavailable"));
            return result;
        } catch (RuntimeException exception) {
            mine.completeExceptionally(exception);
            throw exception;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private PoolLoadResult distributedBuild(String key, Supplier<RecommendationPool> builder, long readMs) {
        String lockKey = key + LOCK_SUFFIX;
        String token = UUID.randomUUID().toString();
        boolean lockOwned = tryLock(lockKey, token);
        if (!lockOwned) {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(properties.getWaitTimeoutMillis());
            while (System.nanoTime() < deadline) {
                sleep(properties.getWaitIntervalMillis());
                RecommendationPool fresh = read(key);
                if (fresh != null) return new PoolLoadResult(fresh, key, true, false, false, readMs, 0L);
            }
            RecommendationPool stale = read(key + STALE_SUFFIX);
            if (stale != null) return new PoolLoadResult(stale, key + STALE_SUFFIX, true, true, false, readMs, 0L);
            log.warn("recommend_pool_distributed_wait_timeout keyHash={} waitMs={}",
                    Integer.toHexString(key.hashCode()), properties.getWaitTimeoutMillis());
            return PoolLoadResult.unavailable(readMs);
        }

        try {
            RecommendationPool pool = builder.get();
            long writeStarted = System.nanoTime();
            write(key, pool);
            return new PoolLoadResult(pool, key, false, false, true, readMs, millis(writeStarted));
        } finally {
            release(lockKey, token);
        }
    }

    /** Redis INCRBY 为每个并发请求分配互不重叠的区间。 */
    public PoolSlice claim(String key, String sourceKey, RecommendationPool pool, int batchSize) {
        if (pool == null || pool.items().isEmpty()) return new PoolSlice(List.of(), 0, true);
        try {
            Long end = redisTemplate.opsForValue().increment(key + CURSOR_SUFFIX, batchSize);
            if (end == null) return fallbackSlice(pool, batchSize);
            long start = Math.max(0L, end - batchSize);
            alignCursorTtl(key + CURSOR_SUFFIX, sourceKey);
            if (start >= pool.items().size()) return new PoolSlice(List.of(), Math.toIntExact(start), true);
            int from = Math.toIntExact(start);
            int to = Math.min(pool.items().size(), Math.toIntExact(end));
            return new PoolSlice(pool.items().subList(from, to), from, false);
        } catch (RuntimeException exception) {
            log.warn("recommend_pool_cursor_failed keyHash={} reason={}",
                    Integer.toHexString(key.hashCode()), exception.getClass().getSimpleName());
            return fallbackSlice(pool, batchSize);
        }
    }

    public boolean invalidateIfCurrent(String key, RecommendationPool pool) {
        try {
            String json = objectMapper.writeValueAsString(pool);
            return Long.valueOf(1L).equals(redisTemplate.execute(INVALIDATE_CURRENT,
                    List.of(key, key + CURSOR_SUFFIX), json));
        } catch (Exception exception) {
            log.warn("recommend_pool_invalidate_failed keyHash={} reason={}",
                    Integer.toHexString(key.hashCode()), exception.getClass().getSimpleName());
            return false;
        }
    }

    public Set<Long> seenTripIds(Long userId) {
        try {
            List<String> values = redisTemplate.opsForList().range(SEEN_PREFIX + userId, 0, -1);
            if (values == null || values.isEmpty()) return Set.of();
            Set<Long> result = new HashSet<>();
            values.forEach(value -> {
                try { result.add(Long.valueOf(value)); } catch (NumberFormatException ignored) { }
            });
            return result;
        } catch (RuntimeException exception) {
            log.warn("recommend_seen_read_failed userId={}", userId);
            return Set.of();
        }
    }

    public void markSeen(Long userId, List<Long> tripIds) {
        if (tripIds == null || tripIds.isEmpty()) return;
        String key = SEEN_PREFIX + userId;
        try {
            redisTemplate.opsForList().rightPushAll(key, tripIds.stream().map(String::valueOf).toList());
            redisTemplate.opsForList().trim(key, -properties.getSeenMaxSize(), -1);
            redisTemplate.expire(key, Duration.ofSeconds(properties.getSeenTtlSeconds()));
        } catch (RuntimeException exception) {
            log.warn("recommend_seen_write_failed userId={} count={}", userId, tripIds.size());
        }
    }

    /** 关键候选变化只推进版本，不扫描或同步删除大量用户 key。 */
    public void invalidateAllPools() {
        try { redisTemplate.opsForValue().increment(VERSION_KEY); }
        catch (RuntimeException exception) {
            log.warn("recommend_pool_version_increment_failed reason={}", exception.getClass().getSimpleName());
        }
    }

    public long nextTtlSeconds() {
        int jitter = properties.getTtlJitterSeconds();
        return properties.getTtlSeconds() + (jitter == 0 ? 0 : ThreadLocalRandom.current().nextInt(jitter + 1));
    }

    private void write(String key, RecommendationPool pool) {
        try {
            String json = objectMapper.writeValueAsString(pool);
            redisTemplate.execute(WRITE_POOL, List.of(key, key + STALE_SUFFIX, key + CURSOR_SUFFIX),
                    json, String.valueOf(nextTtlSeconds()), String.valueOf(properties.getStaleTtlSeconds()));
        } catch (Exception exception) {
            log.warn("recommend_pool_write_failed keyHash={} reason={}",
                    Integer.toHexString(key.hashCode()), exception.getClass().getSimpleName());
        }
    }

    private RecommendationPool read(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            return json == null || json.isBlank() ? null : objectMapper.readValue(json, RecommendationPool.class);
        } catch (Exception exception) {
            log.warn("recommend_pool_read_failed keyHash={} reason={}",
                    Integer.toHexString(key.hashCode()), exception.getClass().getSimpleName());
            return null;
        }
    }

    private long currentVersion() {
        try {
            String value = redisTemplate.opsForValue().get(VERSION_KEY);
            return value == null ? 0L : Long.parseLong(value);
        } catch (RuntimeException exception) {
            log.warn("recommend_pool_version_read_failed reason={}", exception.getClass().getSimpleName());
            return 0L;
        }
    }

    private boolean tryLock(String key, String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    key, token, Duration.ofSeconds(properties.getLockTtlSeconds())));
        } catch (DataAccessException exception) {
            log.warn("recommend_pool_lock_unavailable keyHash={}", Integer.toHexString(key.hashCode()));
            return true;
        }
    }

    private void release(String key, String token) {
        try { redisTemplate.execute(RELEASE_LOCK, List.of(key), token); }
        catch (DataAccessException exception) {
            log.warn("recommend_pool_lock_release_failed keyHash={}", Integer.toHexString(key.hashCode()));
        }
    }

    private void alignCursorTtl(String cursorKey, String sourceKey) {
        Long ttl = redisTemplate.getExpire(sourceKey, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0) redisTemplate.expire(cursorKey, Duration.ofSeconds(ttl));
        else redisTemplate.expire(cursorKey, Duration.ofSeconds(properties.getTtlSeconds()));
    }

    private PoolSlice fallbackSlice(RecommendationPool pool, int batchSize) {
        return new PoolSlice(pool.items().subList(0, Math.min(batchSize, pool.items().size())), 0, false);
    }

    private void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    private long millis(long started) { return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started); }

    public record PoolLoadResult(RecommendationPool pool, String sourceKey, boolean cacheHit, boolean stale,
                                 boolean built, long cacheReadMs, long cacheWriteMs) {
        static PoolLoadResult unavailable(long readMs) {
            return new PoolLoadResult(null, null, true, false, false, readMs, 0L);
        }
    }

    public record PoolSlice(List<RecommendationPool.RecommendationPoolItem> items, int start, boolean exhausted) { }
}
