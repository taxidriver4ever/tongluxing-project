package com.tongluxing.application.listener;

import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
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

    @Async("tripEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPublished(TripPublishedEvent event) {
        matchService.generateTripRecommendations(event.tripId());
    }

    @Async("tripEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onStarted(TripStartedEvent event) {
        matchService.recordTripLifecycle(event.tripId(), "START");
    }

    @Async("tripEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onFinished(TripFinishedEvent event) {
        matchService.recordTripLifecycle(event.tripId(), "FINISH");
    }

    @Async("tripEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTeamApplicationReviewed(TeamApplicationReviewedEvent event) {
        matchService.recordTeamApplication(event.applicantUserId(), event.targetTripId(), event.teamId(), event.status());
    }
}
