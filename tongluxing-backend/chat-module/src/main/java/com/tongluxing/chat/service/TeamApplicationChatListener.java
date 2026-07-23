package com.tongluxing.chat.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.tongluxing.team.service.TeamApplicationReviewedEvent;

import lombok.RequiredArgsConstructor;

/** 审核通过后自动授予行程群聊权限，拒绝时不创建聊天成员。 */
@Component
@RequiredArgsConstructor
public class TeamApplicationChatListener {

    private final ChatService chatService;

    @EventListener
    public void onReviewed(TeamApplicationReviewedEvent event) {
        if ("APPROVED".equals(event.status())) {
            chatService.addApprovedTripMember(event.targetTripId(), event.applicantUserId());
        }
    }
}
