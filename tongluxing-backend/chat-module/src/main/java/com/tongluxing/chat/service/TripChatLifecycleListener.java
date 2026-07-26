package com.tongluxing.chat.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.tongluxing.trip.service.TripFinishedEvent;
import com.tongluxing.trip.service.TripPublishedEvent;
import com.tongluxing.trip.service.TripStartedEvent;
import com.tongluxing.trip.service.TripUpdatedEvent;
import lombok.RequiredArgsConstructor;

/** 将行程状态流转与聊天群生命周期连接起来，避免 trip-module 反向依赖 chat-module。 */
@Component
@RequiredArgsConstructor
public class TripChatLifecycleListener {
    private final ChatService chatService;

    @EventListener
    public void onTripPublished(TripPublishedEvent event) {
        chatService.prepareTripConversation(event.tripId(), event.tripName(), event.ownerUserId(), event.memberUserIds());
    }

    @EventListener
    public void onTripStarted(TripStartedEvent event) {
        chatService.openTripConversation(event.tripId(), event.tripName(), event.ownerUserId(), event.memberUserIds());
    }

    @EventListener
    public void onTripFinished(TripFinishedEvent event) {
        chatService.closeTripConversation(event.tripId());
    }

    @EventListener
    public void onTripUpdated(TripUpdatedEvent event) {
        chatService.notifyTripUpdated(event.tripId(), event.operatorUserId());
    }
}
