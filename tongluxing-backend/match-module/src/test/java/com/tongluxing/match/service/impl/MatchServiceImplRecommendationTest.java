package com.tongluxing.match.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.match.integration.MatchTeamPort;
import com.tongluxing.match.integration.MatchTeamPort.MatchTeamDTO;
import com.tongluxing.match.integration.MatchTripPort;
import com.tongluxing.match.integration.MatchTripPort.MatchRecommendationCandidateDTO;
import com.tongluxing.match.integration.MatchTripPort.MatchTripDTO;
import com.tongluxing.match.config.RecommendationPoolProperties;
import com.tongluxing.match.mapper.MatchRecommendLogMapper;
import com.tongluxing.match.mapper.MatchResultMapper;
import com.tongluxing.match.mapper.TripConsultationMapper;
import com.tongluxing.match.mapper.TripFavoriteMapper;
import com.tongluxing.match.mapper.TripRecommendationMetricsMapper;
import com.tongluxing.match.mapper.TripRecommendationMetricsMapper.TripRecommendationMetricRow;
import com.tongluxing.match.mapper.TripSearchHistoryMapper;
import com.tongluxing.match.service.RecommendImpressionService;
import com.tongluxing.match.service.RecommendationPool;
import com.tongluxing.match.service.RecommendationPoolService;
import com.tongluxing.match.service.RecommendationPoolService.PoolLoadResult;
import com.tongluxing.match.service.RecommendationPoolService.PoolSlice;
import com.tongluxing.match.vo.TripRecommendPageResponse;
import com.tongluxing.user.service.UserService;
import com.tongluxing.user.support.CurrentUserContext;

class MatchServiceImplRecommendationTest {

    @Test
    void poolRefreshesReturnNextBatchWithoutRecalculatingUntilExhausted() {
        MatchTripPort tripPort = mock(MatchTripPort.class);
        MatchTeamPort teamPort = mock(MatchTeamPort.class);
        CurrentUserContext currentUser = mock(CurrentUserContext.class);
        TripRecommendationMetricsMapper metricsMapper = mock(TripRecommendationMetricsMapper.class);
        RecommendationPoolService poolService = mock(RecommendationPoolService.class);
        RecommendationPoolProperties poolProperties = new RecommendationPoolProperties();
        poolProperties.setMaxSize(100);
        RecommendImpressionService impressions = mock(RecommendImpressionService.class);
        MatchServiceImpl service = new MatchServiceImpl(tripPort, teamPort, mock(MatchResultMapper.class),
                mock(MatchRecommendLogMapper.class), currentUser, new ObjectMapper(), mock(UserService.class),
                mock(TripFavoriteMapper.class), mock(TripConsultationMapper.class), metricsMapper,
                mock(TripSearchHistoryMapper.class), poolService, poolProperties, impressions);

        LocalDateTime departure = LocalDateTime.now().plusHours(3);
        List<MatchRecommendationCandidateDTO> candidates = IntStream.rangeClosed(1, 20)
                .mapToObj(id -> candidate((long) id, departure)).toList();
        List<Long> candidateIds = candidates.stream().map(MatchRecommendationCandidateDTO::tripId).toList();
        when(currentUser.requireUserId()).thenReturn(7L);
        when(tripPort.findRecommendationReferenceTrip(7L)).thenReturn(null);
        when(tripPort.listRecommendationCandidates(any())).thenReturn(candidates);
        when(teamPort.findActiveTeamsByTripIds(anyList())).thenAnswer(invocation ->
                ((List<Long>) invocation.getArgument(0)).stream().collect(Collectors.toMap(id -> id, this::team)));
        when(metricsMapper.findByTripIds(candidateIds)).thenReturn(candidateIds.stream()
                .map(id -> metric(id, id.intValue(), 0)).toList());
        when(teamPort.relationshipStatuses(anyList(), any())).thenAnswer(invocation ->
                ((List<MatchTeamDTO>) invocation.getArgument(0)).stream()
                        .collect(Collectors.toMap(MatchTeamDTO::teamId, team -> "NONE")));
        when(tripPort.getTripDetails(anyList())).thenAnswer(invocation ->
                ((List<Long>) invocation.getArgument(0)).stream().collect(Collectors.toMap(id -> id, this::detail)));
        when(poolService.poolKey(any(), any(), any(), any())).thenReturn("recommend:pool:test");
        when(poolService.seenTripIds(7L)).thenReturn(Set.of());
        AtomicReference<RecommendationPool> currentPool = new AtomicReference<>();
        AtomicInteger cursor = new AtomicInteger();
        AtomicInteger builds = new AtomicInteger();
        when(poolService.getOrBuild(any(), any())).thenAnswer(invocation -> {
            boolean hit = currentPool.get() != null;
            if (!hit) {
                @SuppressWarnings("unchecked")
                Supplier<RecommendationPool> supplier = invocation.getArgument(1);
                currentPool.set(supplier.get());
                builds.incrementAndGet();
            }
            return new PoolLoadResult(currentPool.get(), "recommend:pool:test", hit, false,
                    !hit, 1L, hit ? 0L : 1L);
        });
        when(poolService.claim(any(), any(), any(), any(Integer.class))).thenAnswer(invocation -> {
            RecommendationPool pool = invocation.getArgument(2);
            int batchSize = invocation.getArgument(3);
            int start = cursor.getAndAdd(batchSize);
            if (start >= pool.items().size()) return new PoolSlice(List.of(), start, true);
            return new PoolSlice(pool.items().subList(start, Math.min(start + batchSize, pool.items().size())),
                    start, false);
        });
        when(poolService.invalidateIfCurrent(any(), any())).thenAnswer(invocation -> {
            currentPool.set(null);
            cursor.set(0);
            return true;
        });

        TripRecommendPageResponse first = service.recommendTrips("heat", false, 23D, 113D, 1, 10);
        TripRecommendPageResponse second = service.recommendTrips("heat", false, 23D, 113D, 2, 10);

        assertEquals(20L, first.total());
        assertEquals("HEAT", first.effectiveSort());
        assertFalse(first.userHasTrip());
        assertEquals(IntStream.iterate(20, value -> value - 1).limit(10).mapToObj(String::valueOf).toList(),
                first.list().stream().map(card -> card.tripId()).toList());
        assertEquals(IntStream.iterate(10, value -> value - 1).limit(10).mapToObj(String::valueOf).toList(),
                second.list().stream().map(card -> card.tripId()).toList());
        assertEquals(1, builds.get());
        verify(tripPort, times(1)).listRecommendationCandidates(any());

        TripRecommendPageResponse afterExhaustion = service.recommendTrips("heat", false, 23D, 113D, 3, 10);
        assertEquals(10, afterExhaustion.list().size());
        assertEquals(2, builds.get());
        verify(tripPort, times(2)).listRecommendationCandidates(any());

        verify(teamPort, never()).relationshipStatus(any(), any());
        verify(tripPort, never()).listPublicTrips(any(MatchTripPort.MatchCandidateQuery.class));
        ArgumentCaptor<List<RecommendImpressionService.ImpressionCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(impressions, times(3)).submit(captor.capture());
        assertEquals(List.of(10, 10, 10), captor.getAllValues().stream().map(List::size).toList());
    }

    @Test
    void keepsHeatScoreSemantics() {
        assertEquals(80, metric(1L, 2, 0).applicationCount() * 40);
        assertEquals(60, metric(2L, 0, 2).favoriteCount() * 30);
    }

    private MatchRecommendationCandidateDTO candidate(Long id, LocalDateTime departure) {
        return new MatchRecommendationCandidateDTO(id, id + 10, 23D, 113D, 23.1D, 113.1D,
                departure, 1, 100_000, "[]", "LIGHT", 4, 1, "PUBLISHED");
    }

    private MatchTeamDTO team(Long tripId) {
        return new MatchTeamDTO(tripId + 100, tripId, tripId + 10, "Team " + tripId,
                null, null, "Start", "End", null, 1, 4, "OPEN", true);
    }

    private TripRecommendationMetricRow metric(Long id, int applications, int favorites) {
        return new TripRecommendationMetricRow(id, applications, favorites, 4.8D, 0D, 10);
    }

    private MatchTripDTO detail(Long id) {
        MatchTripDTO detail = mock(MatchTripDTO.class);
        when(detail.tripId()).thenReturn(id);
        when(detail.userId()).thenReturn(id + 10);
        when(detail.ownerNickname()).thenReturn("Owner " + id);
        when(detail.title()).thenReturn("Trip " + id);
        when(detail.startName()).thenReturn("Start");
        when(detail.endName()).thenReturn("End");
        when(detail.departureTime()).thenReturn(LocalDateTime.now().plusHours(3));
        when(detail.joinedVehicleCount()).thenReturn(1);
        when(detail.maxVehicleCount()).thenReturn(4);
        when(detail.status()).thenReturn("PUBLISHED");
        return detail;
    }
}
