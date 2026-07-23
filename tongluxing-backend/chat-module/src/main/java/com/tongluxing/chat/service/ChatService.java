package com.tongluxing.chat.service;

import java.util.List;

import com.tongluxing.chat.dto.SendMessageRequest;
import com.tongluxing.chat.dto.TeamConversationRequest;
import com.tongluxing.chat.vo.ConversationListResponse;
import com.tongluxing.chat.vo.ConversationMemberResponse;
import com.tongluxing.chat.vo.ConversationResponse;
import com.tongluxing.chat.vo.MessageListResponse;
import com.tongluxing.chat.vo.MessageResponse;
import com.tongluxing.chat.vo.ConversationSettingResponse;
import com.tongluxing.chat.vo.JoinApplicationResponse;

/**
 * 聊天业务服务。
 *
 * <p>封装本地会话、成员和消息的业务规则；第三方 IM 同步由实现类调用 {@link TencentImService} 完成。</p>
 */
public interface ChatService {

    /** 创建或查询车队对应的群聊会话。 */
    ConversationResponse createTeamConversation(TeamConversationRequest request);

    /** 行程开始时创建或重新启用行程群聊。 */
    ConversationResponse openTripConversation(Long tripId, String tripName, Long ownerUserId, List<Long> memberUserIds);

    /** 行程发布时建立出发前群聊，用于讨论和行程确认。 */
    ConversationResponse prepareTripConversation(Long tripId, String tripName, Long ownerUserId, List<Long> memberUserIds);

    /** 查询当前成员可访问的行程群聊。 */
    ConversationResponse getTripConversation(Long tripId);

    /** 行程结束时写入系统消息并转换为可继续聊天的历史群。 */
    void closeTripConversation(Long tripId);

    /** 查询当前登录用户参与的有效会话。 */
    ConversationListResponse getConversations(String title);

    /** 查询会话历史消息。 */
    MessageListResponse getMessages(Long conversationId, Long beforeMessageId, Integer limit);

    /** 当前登录用户向会话发送消息。 */
    MessageResponse sendMessage(Long conversationId, SendMessageRequest request);

    /** 添加用户为会话成员。 */
    ConversationMemberResponse addMember(Long conversationId, Long userId);

    /** 行程入队申请通过后，由内部事件授予对应群聊成员权限。 */
    void addApprovedTripMember(Long tripId, Long userId);

    /** 当前登录用户退出会话。 */
    void exitMe(Long conversationId);

    /** 查询会话成员及公开资料摘要。 */
    List<ConversationMemberResponse> getMembers(Long conversationId);

    /** 查询当前成员的会话设置。 */
    ConversationSettingResponse getSettings(Long conversationId);

    /** 更新当前成员的会话设置。 */
    ConversationSettingResponse updateSettings(Long conversationId, boolean muted, boolean pinned);

    /** 当前用户申请加入群聊。 */
    JoinApplicationResponse applyToJoin(Long conversationId, String message);

    /** 队长查询待处理入群申请。 */
    List<JoinApplicationResponse> getJoinApplications(String status);

    /** 队长审批入群申请。 */
    JoinApplicationResponse reviewJoinApplication(Long applicationId, String decision);
}
