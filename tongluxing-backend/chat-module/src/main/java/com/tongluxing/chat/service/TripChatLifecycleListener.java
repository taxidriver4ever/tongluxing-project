package com.tongluxing.chat.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import com.tongluxing.trip.service.TripFinishedEvent;
import com.tongluxing.trip.service.TripPublishedEvent;
import com.tongluxing.trip.service.TripStartedEvent;
import com.tongluxing.trip.service.TripUpdatedEvent;
import lombok.RequiredArgsConstructor;

/**
 * 行程与聊天会话生命周期适配器。
 *
 * <p>trip-module 只发布领域事件，不感知聊天表或腾讯 IM；本监听器把发布、开始、
 * 结束和资料修改分别转换为聊天服务操作，从而保持模块依赖方向单一。</p>
 */
@Component
@RequiredArgsConstructor
public class TripChatLifecycleListener {
    private final ChatService chatService;

    @EventListener
    public void onTripPublished(TripPublishedEvent event) {
        // 乘客发布的只是出行需求，在匹配到车主前没有队长，也不创建群聊。
        if (!event.hasCaptain()) {
            return;
        }
        // 车主发布后准备群聊，让成员能在正式出发前沟通。
        chatService.prepareTripConversation(event.tripId(), event.tripName(), event.ownerUserId(), event.memberUserIds());
    }

    @EventListener
    public void onTripStarted(TripStartedEvent event) {
        // 仅同步群成员与状态；业务提醒由 APP 系统推送承担，不再依赖群系统消息。
        chatService.openTripConversation(event.tripId(), event.tripName(), event.ownerUserId(), event.memberUserIds());
    }

    @EventListener
    public void onTripFinished(TripFinishedEvent event) {
        // 结束后转成历史群而非删除，让成员仍能查看消息并继续交流。
        chatService.closeTripConversation(event.tripId());
    }

    @EventListener
    public void onTripUpdated(TripUpdatedEvent event) {
        // P0 最终方案取消群系统提醒。行程更新通过系统推送通知，群聊只承载用户消息。
        // 这里故意不调用 notifyTripUpdated。
    }
}
