package com.tongluxing;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.tongluxing.match.service.MatchService;
import com.tongluxing.trip.service.TripFinishedEvent;
import com.tongluxing.trip.service.TripPublishedEvent;
import com.tongluxing.trip.service.TripStartedEvent;
import com.tongluxing.team.service.TeamApplicationReviewedEvent;
import lombok.RequiredArgsConstructor;

/** 把行程生命周期事件接入发现同行推荐生成与转化漏斗。 */
@Component @RequiredArgsConstructor
public class MatchTripLifecycleListener {
    private final MatchService matchService;

    @EventListener
    public void onPublished(TripPublishedEvent event) {
        matchService.generateTripRecommendations(event.tripId());
    }

    @EventListener
    public void onStarted(TripStartedEvent event) {
        matchService.recordTripLifecycle(event.tripId(), "START");
    }

    @EventListener
    public void onFinished(TripFinishedEvent event) {
        matchService.recordTripLifecycle(event.tripId(), "FINISH");
    }

    @EventListener
    public void onTeamApplicationReviewed(TeamApplicationReviewedEvent event) {
        matchService.recordTeamApplication(event.applicantUserId(), event.targetTripId(), event.teamId(), event.status());
    }
}
