package com.tongluxing.match.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.match.config.RecommendationCacheProperties;
import com.tongluxing.match.vo.TripRecommendPageResponse;

class RecommendationCacheServiceTest {

    @Test
    void sameKeyConcurrentRequestsOnlyComputeOnce() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Map<String, String> storage = new ConcurrentHashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenAnswer(invocation -> storage.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            storage.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        RecommendationCacheProperties properties = properties();
        RecommendationCacheService service = new RecommendationCacheService(redis, new ObjectMapper(), properties);
        AtomicInteger computations = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(12);
        try {
            var futures = IntStream.range(0, 12).mapToObj(index -> executor.submit(() -> {
                start.await();
                return service.getOrCompute("recommend:test", () -> {
                    computations.incrementAndGet();
                    entered.countDown();
                    await(release);
                    return emptyResponse();
                }).value();
            })).toList();
            start.countDown();
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            Thread.sleep(100L);
            release.countDown();
            for (var future : futures) assertEquals(emptyResponse(), future.get(3, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, computations.get());
    }

    @Test
    void ttlIsAlwaysInsideConfiguredRandomRange() {
        RecommendationCacheService service = new RecommendationCacheService(
                mock(StringRedisTemplate.class), new ObjectMapper(), properties());
        Set<Long> generated = IntStream.range(0, 500).mapToObj(index -> service.nextTtlSeconds())
                .collect(Collectors.toSet());
        assertTrue(generated.stream().allMatch(value -> value >= 60 && value <= 90));
        assertTrue(generated.size() > 1, "jitter should produce more than one TTL value");
    }

    private RecommendationCacheProperties properties() {
        RecommendationCacheProperties properties = new RecommendationCacheProperties();
        properties.setTtlSeconds(60);
        properties.setTtlJitterSeconds(30);
        properties.setLockTtlSeconds(20);
        properties.setWaitTimeoutMillis(500);
        properties.setWaitIntervalMillis(5);
        properties.setStaleTtlSeconds(300);
        return properties;
    }

    private TripRecommendPageResponse emptyResponse() {
        return new TripRecommendPageResponse(0L, List.of(), false, "HEAT", null);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
