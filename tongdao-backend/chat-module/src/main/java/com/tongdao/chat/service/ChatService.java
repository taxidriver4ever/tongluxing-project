package com.tongdao.chat.service;

import com.tongdao.chat.dto.SendMessageRequest;
import com.tongdao.chat.dto.TeamConversationRequest;
import com.tongdao.chat.vo.ConversationListResponse;
import com.tongdao.chat.vo.ConversationMemberResponse;
import com.tongdao.chat.vo.ConversationResponse;
import com.tongdao.chat.vo.MessageListResponse;
import com.tongdao.chat.vo.MessageResponse;

public interface ChatService {

    ConversationResponse createTeamConversation(TeamConversationRequest request);

    ConversationListResponse getConversations();

    MessageListResponse getMessages(Long conversationId, Long beforeMessageId, Integer limit);

    MessageResponse sendMessage(Long conversationId, SendMessageRequest request);

    ConversationMemberResponse addMember(Long conversationId, Long userId);

    void exitMe(Long conversationId);
}
