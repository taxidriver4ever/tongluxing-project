package com.tongluxing.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.match.config.RecommendationPoolProperties;
import com.tongluxing.match.service.RecommendationPool.RecommendationPoolItem;

class RecommendationPoolServiceTest {

    @Test
    void sameKeyConcurrentMissBuildsPoolOnlyOnce() throws Exception {
        RedisMocks mocks = redisMocks();
        when(mocks.values().get(anyString())).thenReturn(null);
        when(mocks.values().setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        RecommendationPoolService service = new RecommendationPoolService(
                mocks.redis(), new ObjectMapper(), properties());
        AtomicInteger builds = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch ready = new CountDownLatch(12);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(12);
        try {
            var futures = IntStream.range(0, 12).mapToObj(index -> executor.submit(() -> {
                start.await();
                ready.countDown();
                return service.getOrBuild("recommend:pool:test", () -> {
                    builds.incrementAndGet();
                    entered.countDown();
                    await(release);
                    return pool("generation-1", 20);
                }).pool();
            })).toList();
            start.countDown();
            assertTrue(ready.await(2, TimeUnit.SECONDS));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            Thread.sleep(100L);
            release.countDown();
            for (var future : futures) assertEquals("generation-1", future.get(2, TimeUnit.SECONDS).generationId());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, builds.get());
    }

    @Test
    void atomicCursorReturnsNonOverlappingBatches() throws Exception {
        RedisMocks mocks = redisMocks();
        AtomicLong cursor = new AtomicLong();
        when(mocks.values().increment(anyString(), any(Long.class)))
                .thenAnswer(invocation -> cursor.addAndGet(invocation.getArgument(1)));
        when(mocks.redis().getExpire(anyString(), any(TimeUnit.class))).thenReturn(180L);
        RecommendationPoolService service = new RecommendationPoolService(
                mocks.redis(), new ObjectMapper(), properties());
        RecommendationPool pool = pool("generation-1", 20);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.claim("pool", "pool", pool, 10));
            var second = executor.submit(() -> service.claim("pool", "pool", pool, 10));
            Set<Long> left = first.get().items().stream().map(RecommendationPoolItem::tripId).collect(Collectors.toSet());
            Set<Long> right = second.get().items().stream().map(RecommendationPoolItem::tripId).collect(Collectors.toSet());
            assertEquals(10, left.size());
            assertEquals(10, right.size());
            Set<Long> intersection = new HashSet<>(left);
            intersection.retainAll(right);
            assertTrue(intersection.isEmpty());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void stalePoolIsTemporaryFallbackAndFreshWinsWhenAvailable() throws Exception {
        RedisMocks mocks = redisMocks();
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> storage = new ConcurrentHashMap<>();
        storage.put("pool:stale", objectMapper.writeValueAsString(pool("stale-generation", 5)));
        when(mocks.values().get(anyString())).thenAnswer(invocation -> storage.get(invocation.getArgument(0)));
        when(mocks.values().setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        RecommendationPoolProperties properties = properties();
        properties.setWaitTimeoutMillis(20);
        properties.setWaitIntervalMillis(5);
        RecommendationPoolService service = new RecommendationPoolService(mocks.redis(), objectMapper, properties);

        var stale = service.getOrBuild("pool", () -> pool("unused", 5));
        assertTrue(stale.stale());
        assertEquals("stale-generation", stale.pool().generationId());

        storage.put("pool", objectMapper.writeValueAsString(pool("fresh-generation", 5)));
        var fresh = service.getOrBuild("pool", () -> pool("unused", 5));
        assertFalse(fresh.stale());
        assertEquals("fresh-generation", fresh.pool().generationId());
    }

    @Test
    void distributedFollowerStopsAfterShortConfiguredWait() {
        RedisMocks mocks = redisMocks();
        when(mocks.values().get(anyString())).thenReturn(null);
        when(mocks.values().setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);
        RecommendationPoolProperties properties = properties();
        properties.setWaitTimeoutMillis(60);
        properties.setWaitIntervalMillis(5);
        RecommendationPoolService service = new RecommendationPoolService(
                mocks.redis(), new ObjectMapper(), properties);

        long started = System.nanoTime();
        var result = service.getOrBuild("pool", () -> pool("must-not-build", 5));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertNull(result.pool());
        assertTrue(elapsedMillis < 300, "follower must not wait for lock TTL");
    }

    @Test
    void ttlIsInsideConfiguredRandomRange() {
        RecommendationPoolService service = new RecommendationPoolService(
                mock(StringRedisTemplate.class), new ObjectMapper(), properties());
        Set<Long> generated = IntStream.range(0, 500).mapToObj(index -> service.nextTtlSeconds())
                .collect(Collectors.toSet());
        assertTrue(generated.stream().allMatch(value -> value >= 180 && value <= 240));
        assertNotEquals(1, generated.size());
    }

    private RecommendationPool pool(String generation, int size) {
        return new RecommendationPool(generation, false, "HEAT", null,
                IntStream.rangeClosed(1, size).mapToObj(index ->
                        new RecommendationPoolItem((long) index, null, 100 - index, index,
                                (long) index, 4.8D)).toList());
    }

    private RecommendationPoolProperties properties() {
        RecommendationPoolProperties properties = new RecommendationPoolProperties();
        properties.setMaxSize(100);
        properties.setTtlSeconds(180);
        properties.setTtlJitterSeconds(60);
        properties.setStaleTtlSeconds(600);
        properties.setLockTtlSeconds(30);
        properties.setWaitTimeoutMillis(500);
        properties.setWaitIntervalMillis(5);
        properties.setSeenMaxSize(200);
        properties.setSeenTtlSeconds(1200);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private RedisMocks redisMocks() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        return new RedisMocks(redis, values);
    }

    private static void await(CountDownLatch latch) {
        try { latch.await(2, TimeUnit.SECONDS); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    private record RedisMocks(StringRedisTemplate redis, ValueOperations<String, String> values) { }
}
