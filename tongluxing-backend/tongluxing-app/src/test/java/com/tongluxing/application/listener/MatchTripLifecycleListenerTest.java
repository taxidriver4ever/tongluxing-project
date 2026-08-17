package com.tongluxing.application.listener;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import com.tongluxing.match.service.MatchService;
import com.tongluxing.match.service.RecommendationPoolService;
import com.tongluxing.trip.service.TripRecommendationChangedEvent;

class MatchTripLifecycleListenerTest {

    @Test
    void coreTripChangeAdvancesRecommendationPoolVersion() {
        RecommendationPoolService poolService = mock(RecommendationPoolService.class);
        MatchTripLifecycleListener listener = new MatchTripLifecycleListener(
                mock(MatchService.class), poolService);

        listener.onRecommendationChanged(new TripRecommendationChangedEvent(11L, 7L, "ROUTE_UPDATE"));

        verify(poolService).invalidateAllPools();
    }
}
