package com.tongdao.chat.service;

import com.tongdao.chat.dto.SendMessageRequest;
import com.tongdao.chat.dto.TeamConversationRequest;
import com.tongdao.chat.vo.ConversationListResponse;
import com.tongdao.chat.vo.ConversationMemberResponse;
import com.tongdao.chat.vo.ConversationResponse;
import com.tongdao.chat.vo.MessageListResponse;
import com.tongdao.chat.vo.MessageResponse;

/**
 * 聊天业务服务。
 *
 * <p>封装本地会话、成员和消息的业务规则；第三方 IM 同步由实现类调用 {@link TencentImService} 完成。</p>
 */
public interface ChatService {

    /** 创建或查询车队对应的群聊会话。 */
    ConversationResponse createTeamConversation(TeamConversationRequest request);

    /** 查询当前登录用户参与的有效会话。 */
    ConversationListResponse getConversations();

    /** 查询会话历史消息。 */
    MessageListResponse getMessages(Long conversationId, Long beforeMessageId, Integer limit);

    /** 当前登录用户向会话发送消息。 */
    MessageResponse sendMessage(Long conversationId, SendMessageRequest request);

    /** 添加用户为会话成员。 */
    ConversationMemberResponse addMember(Long conversationId, Long userId);

    /** 当前登录用户退出会话。 */
    void exitMe(Long conversationId);
}
