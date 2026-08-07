package com.tongluxing.chat.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.stereotype.Component;

import com.tongluxing.team.service.TeamMemberRemovedEvent;

import lombok.RequiredArgsConstructor;

/** 队长踢人后同步移除本地会话成员和腾讯 IM 群成员。 */
@Component
@RequiredArgsConstructor
public class TeamMemberRemovedChatListener {

    private final ChatService chatService;

    @Async("tripEventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMemberRemoved(TeamMemberRemovedEvent event) {
        chatService.removeTripMember(event.tripId(), event.memberUserId());
    }
}
