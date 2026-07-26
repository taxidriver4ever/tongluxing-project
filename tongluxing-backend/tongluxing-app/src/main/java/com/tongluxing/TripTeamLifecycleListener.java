package com.tongluxing;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.tongluxing.team.service.TeamService;
import com.tongluxing.trip.service.TripPublishedEvent;

import lombok.RequiredArgsConstructor;

/**
 * 公开行程发布后自动建立可申请加入的车队，保证发现页列表、详情和申请链路一致。
 */
@Component
@RequiredArgsConstructor
public class TripTeamLifecycleListener {

    private final TeamService teamService;

    @EventListener
    public void onTripPublished(TripPublishedEvent event) {
        if (!Boolean.TRUE.equals(event.publicFlag())) {
            return;
        }
        teamService.ensurePublishedTripTeam(
                event.tripId(),
                event.ownerUserId(),
                event.ownerVehicleId(),
                event.tripName() + "车队",
                event.maxMemberCount()
        );
    }
}
