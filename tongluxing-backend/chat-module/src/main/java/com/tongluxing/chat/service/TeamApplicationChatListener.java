package com.tongluxing.chat.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.tongluxing.team.service.TeamApplicationReviewedEvent;

import lombok.RequiredArgsConstructor;

/**
 * 车队申请审核事件监听器。
 *
 * <p>通过领域事件连接 team-module 与 chat-module，避免车队服务直接依赖聊天实现；
 * 只有审批通过才授予聊天成员资格，拒绝事件不会产生任何会话成员数据。</p>
 */
@Component
@RequiredArgsConstructor
public class TeamApplicationChatListener {

    private final ChatService chatService;

    @EventListener
    public void onReviewed(TeamApplicationReviewedEvent event) {
        // 明确匹配 APPROVED，避免未来增加其他审核状态时被误当作通过处理。
        if ("APPROVED".equals(event.status())) {
            // 服务方法自身具备幂等校验，事件重复投递不会重复加群或发送系统消息。
            chatService.addApprovedTripMember(event.targetTripId(), event.applicantUserId());
        }
    }
}
