package com.tongluxing.chat.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.tongluxing.team.service.TeamMemberRemovedEvent;

import lombok.RequiredArgsConstructor;

/** 队长踢人后同步移除本地会话成员和腾讯 IM 群成员。 */
@Component
@RequiredArgsConstructor
public class TeamMemberRemovedChatListener {

    private final ChatService chatService;

    @EventListener
    public void onMemberRemoved(TeamMemberRemovedEvent event) {
        chatService.removeTripMember(event.tripId(), event.memberUserId());
    }
}
