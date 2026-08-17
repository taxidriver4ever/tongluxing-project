package com.tongluxing.match.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.match.integration.MatchTeamPort;
import com.tongluxing.match.integration.MatchTeamPort.MatchTeamDTO;
import com.tongluxing.match.integration.MatchTripPort;
import com.tongluxing.match.integration.MatchTripPort.MatchRecommendationCandidateDTO;
import com.tongluxing.match.integration.MatchTripPort.MatchTripDTO;
import com.tongluxing.match.mapper.MatchRecommendLogMapper;
import com.tongluxing.match.mapper.MatchResultMapper;
import com.tongluxing.match.mapper.TripConsultationMapper;
import com.tongluxing.match.mapper.TripFavoriteMapper;
import com.tongluxing.match.mapper.TripRecommendationMetricsMapper;
import com.tongluxing.match.mapper.TripRecommendationMetricsMapper.TripRecommendationMetricRow;
import com.tongluxing.match.mapper.TripSearchHistoryMapper;
import com.tongluxing.match.service.RecommendImpressionService;
import com.tongluxing.match.service.RecommendationCacheService;
import com.tongluxing.match.service.RecommendationCacheService.CacheResult;
import com.tongluxing.match.vo.TripRecommendPageResponse;
import com.tongluxing.user.service.UserService;
import com.tongluxing.user.support.CurrentUserContext;

class MatchServiceImplRecommendationTest {

    @Test
    void keepsHeatSortAndPaginationWhileLoadingOnlyFinalPageDetails() {
        MatchTripPort tripPort = mock(MatchTripPort.class);
        MatchTeamPort teamPort = mock(MatchTeamPort.class);
        CurrentUserContext currentUser = mock(CurrentUserContext.class);
        TripRecommendationMetricsMapper metricsMapper = mock(TripRecommendationMetricsMapper.class);
        RecommendationCacheService cache = mock(RecommendationCacheService.class);
        RecommendImpressionService impressions = mock(RecommendImpressionService.class);
        MatchServiceImpl service = new MatchServiceImpl(tripPort, teamPort, mock(MatchResultMapper.class),
                mock(MatchRecommendLogMapper.class), currentUser, new ObjectMapper(), mock(UserService.class),
                mock(TripFavoriteMapper.class), mock(TripConsultationMapper.class), metricsMapper,
                mock(TripSearchHistoryMapper.class), cache, impressions);

        LocalDateTime departure = LocalDateTime.now().plusHours(3);
        List<MatchRecommendationCandidateDTO> candidates = List.of(
                candidate(1L, departure), candidate(2L, departure), candidate(3L, departure));
        Map<Long, MatchTeamDTO> teams = Map.of(1L, team(1L), 2L, team(2L), 3L, team(3L));
        when(currentUser.requireUserId()).thenReturn(7L);
        when(tripPort.findRecommendationReferenceTrip(7L)).thenReturn(null);
        when(tripPort.listRecommendationCandidates(any())).thenReturn(candidates);
        when(teamPort.findActiveTeamsByTripIds(List.of(1L, 2L, 3L))).thenReturn(teams);
        when(metricsMapper.findByTripIds(List.of(1L, 2L, 3L))).thenReturn(List.of(
                metric(1L, 1, 0), metric(2L, 0, 2), metric(3L, 2, 0)));
        when(teamPort.relationshipStatuses(anyList(), any())).thenReturn(Map.of(103L, "NONE", 102L, "PENDING"));
        MatchTripDTO detail3 = detail(3L);
        MatchTripDTO detail2 = detail(2L);
        when(tripPort.getTripDetails(List.of(3L, 2L))).thenReturn(Map.of(3L, detail3, 2L, detail2));
        when(cache.getOrCompute(any(), any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<TripRecommendPageResponse> supplier = invocation.getArgument(1);
            return new CacheResult(supplier.get(), false, 1L, 1L, true);
        });

        TripRecommendPageResponse response = service.recommendTrips("heat", false, 23D, 113D, 1, 2);

        assertEquals(3L, response.total());
        assertEquals("HEAT", response.effectiveSort());
        assertFalse(response.userHasTrip());
        assertEquals(List.of("3", "2"), response.list().stream().map(card -> card.tripId()).toList());
        assertEquals(List.of(80, 60), response.list().stream().map(card -> card.heat()).toList());
        verify(tripPort).getTripDetails(List.of(3L, 2L));
        verify(teamPort).relationshipStatuses(anyList(), any());
        verify(teamPort, never()).relationshipStatus(any(), any());
        verify(tripPort, never()).listPublicTrips(any(MatchTripPort.MatchCandidateQuery.class));
        ArgumentCaptor<List<RecommendImpressionService.ImpressionCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(impressions).submit(captor.capture());
        assertEquals(2, captor.getValue().size());
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
