package com.tongdao.chat.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongdao.chat.dto.SendMessageRequest;
import com.tongdao.chat.dto.TeamConversationRequest;
import com.tongdao.chat.entity.ChatConversation;
import com.tongdao.chat.entity.ChatConversationMember;
import com.tongdao.chat.entity.ChatMessage;
import com.tongdao.chat.mapper.ChatConversationMapper;
import com.tongdao.chat.mapper.ChatConversationMemberMapper;
import com.tongdao.chat.mapper.ChatMessageMapper;
import com.tongdao.chat.service.ChatService;
import com.tongdao.chat.service.TencentImService;
import com.tongdao.chat.vo.ConversationListResponse;
import com.tongdao.chat.vo.ConversationMemberResponse;
import com.tongdao.chat.vo.ConversationResponse;
import com.tongdao.chat.vo.MessageListResponse;
import com.tongdao.chat.vo.MessageResponse;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 聊天业务服务实现。
 *
 * <p>本类维护本地会话、成员和消息表；腾讯云 IM 只作为实时通信通道，
 * 本地数据库仍是会话归属、消息摘要和成员状态的事实来源。</p>
 */
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** 前端展示时间格式。 */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 会话 Mapper。 */
    private final ChatConversationMapper conversationMapper;
    /** 会话成员 Mapper。 */
    private final ChatConversationMemberMapper memberMapper;
    /** 消息 Mapper。 */
    private final ChatMessageMapper messageMapper;
    /** 当前登录用户上下文。 */
    private final CurrentUserContext currentUserContext;
    /** JSON 工具，用于消息 payload 序列化和解析。 */
    private final ObjectMapper objectMapper;
    /** 腾讯云 IM 集成服务。 */
    private final TencentImService tencentImService;

    /** 创建或复用车队群聊会话。 */
    @Override
    public ConversationResponse createTeamConversation(TeamConversationRequest request) {
        ChatConversation existed = conversationMapper.findByBiz("TEAM", request.teamId());
        if (existed != null) {
            return toConversationResponse(existed);
        }
        LocalDateTime now = LocalDateTime.now();
        ChatConversation conversation = new ChatConversation();
        conversation.setId(SnowflakeIdGenerator.nextId());
        String groupId = groupId(request.teamId());
        // 先在腾讯云 IM 创建群组，再落本地会话，确保本地 provider key 指向真实 IM 群组。
        tencentImService.createGroup(groupId, TencentImServiceImpl.toImUserId(request.ownerUserId()), request.conversationName());
        conversation.setBizType("TEAM");
        conversation.setBizId(request.teamId());
        conversation.setConversationName(request.conversationName());
        conversation.setConversationStatus("ACTIVE");
        conversation.setProviderType("TENCENT_IM");
        conversation.setProviderConversationKey(groupId);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        addMemberInternal(conversation.getId(), request.ownerUserId(), "OWNER");
        return toConversationResponse(conversation);
    }

    /** 查询当前用户参与的有效会话列表。 */
    @Override
    public ConversationListResponse getConversations() {
        Long userId = currentUserContext.requireUserId();
        return new ConversationListResponse(conversationMapper.findActiveByUserId(userId).stream()
                .map(this::toConversationResponse)
                .toList());
    }

    /** 查询会话历史消息；仅允许有效成员访问。 */
    @Override
    public MessageListResponse getMessages(Long conversationId, Long beforeMessageId, Integer limit) {
        Long userId = currentUserContext.requireUserId();
        requireActiveMember(conversationId, userId);
        // 限制分页大小，防止一次拉取过多消息影响数据库性能。
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        return new MessageListResponse(messageMapper.findMessages(conversationId, beforeMessageId, safeLimit).stream()
                .map(this::toMessageResponse)
                .toList());
    }

    /** 发送消息并刷新会话最后一条消息摘要。 */
    @Override
    public MessageResponse sendMessage(Long conversationId, SendMessageRequest request) {
        Long userId = currentUserContext.requireUserId();
        requireActiveMember(conversationId, userId);
        if (!StringUtils.hasText(request.content())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "消息内容不能为空");
        }
        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = new ChatMessage();
        message.setId(SnowflakeIdGenerator.nextId());
        message.setConversationId(conversationId);
        message.setSenderUserId(userId);
        message.setMessageType(request.messageType());
        message.setMessagePayloadJson(toJson(Map.of("content", request.content())));
        message.setMessageStatus("NORMAL");
        message.setProviderMessageKey("mock-msg-" + message.getId());
        message.setSentAt(now);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        messageMapper.insert(message);
        conversationMapper.updateLastMessage(conversationId, message.getId(), preview(request.content()), now);
        // 给除发送者之外的有效成员增加未读数。
        memberMapper.incrementUnread(conversationId, userId, now);
        return toMessageResponse(message);
    }

    /** 添加会话成员，并同步到腾讯云 IM 群组。 */
    @Override
    public ConversationMemberResponse addMember(Long conversationId, Long userId) {
        ChatConversation conversation = conversationMapper.findById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        if (StringUtils.hasText(conversation.getProviderConversationKey())) {
            tencentImService.addGroupMember(conversation.getProviderConversationKey(), TencentImServiceImpl.toImUserId(userId));
        }
        return toMemberResponse(addMemberInternal(conversationId, userId, "MEMBER"));
    }

    /** 当前用户退出会话，并同步移除腾讯云 IM 群成员。 */
    @Override
    public void exitMe(Long conversationId) {
        Long userId = currentUserContext.requireUserId();
        ChatConversation conversation = conversationMapper.findById(conversationId);
        if (conversation != null && StringUtils.hasText(conversation.getProviderConversationKey())) {
            tencentImService.removeGroupMember(conversation.getProviderConversationKey(), TencentImServiceImpl.toImUserId(userId));
        }
        int changed = memberMapper.exit(conversationId, userId, LocalDateTime.now());
        if (changed == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权退出该会话");
        }
    }

    /** 生成车队群聊在腾讯云 IM 中的群组 ID。 */
    private String groupId(Long teamId) {
        return "team_" + teamId;
    }

    /** 添加或重新激活会话成员。 */
    private ChatConversationMember addMemberInternal(Long conversationId, Long userId, String role) {
        ChatConversationMember existed = memberMapper.findByConversationAndUser(conversationId, userId);
        if (existed != null && "ACTIVE".equals(existed.getMemberStatus())) {
            return existed;
        }
        LocalDateTime now = LocalDateTime.now();
        if (existed != null) {
            memberMapper.reactivate(conversationId, userId, role, now);
            existed.setMemberRole(role);
            existed.setMemberStatus("ACTIVE");
            existed.setUnreadCount(0);
            existed.setJoinedAt(now);
            return existed;
        }
        ChatConversationMember member = new ChatConversationMember();
        member.setId(SnowflakeIdGenerator.nextId());
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setMemberRole(role);
        member.setMemberStatus("ACTIVE");
        member.setUnreadCount(0);
        member.setJoinedAt(now);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        memberMapper.insert(member);
        return member;
    }

    /** 校验用户是会话有效成员，防止越权读取或发送消息。 */
    private ChatConversationMember requireActiveMember(Long conversationId, Long userId) {
        ChatConversationMember member = memberMapper.findByConversationAndUser(conversationId, userId);
        if (member == null || !"ACTIVE".equals(member.getMemberStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该会话");
        }
        return member;
    }

    /** 转换会话响应。 */
    private ConversationResponse toConversationResponse(ChatConversation conversation) {
        return new ConversationResponse(
                String.valueOf(conversation.getId()),
                conversation.getBizType(),
                String.valueOf(conversation.getBizId()),
                conversation.getConversationName(),
                conversation.getConversationStatus(),
                conversation.getProviderType(),
                conversation.getLastMessagePreview(),
                format(conversation.getLastMessageAt())
        );
    }

    /** 转换会话成员响应。 */
    private ConversationMemberResponse toMemberResponse(ChatConversationMember member) {
        return new ConversationMemberResponse(
                String.valueOf(member.getId()),
                String.valueOf(member.getConversationId()),
                String.valueOf(member.getUserId()),
                member.getMemberRole(),
                member.getMemberStatus(),
                member.getUnreadCount(),
                format(member.getJoinedAt())
        );
    }

    /** 转换消息响应。 */
    private MessageResponse toMessageResponse(ChatMessage message) {
        return new MessageResponse(
                String.valueOf(message.getId()),
                String.valueOf(message.getConversationId()),
                message.getSenderUserId() == null ? null : String.valueOf(message.getSenderUserId()),
                message.getMessageType(),
                readContent(message.getMessagePayloadJson()),
                message.getMessageStatus(),
                format(message.getSentAt())
        );
    }

    /** 将消息内容序列化为 payload JSON。 */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "消息序列化失败");
        }
    }

    /** 从消息 payload JSON 中解析文本内容。 */
    private String readContent(String json) {
        try {
            Map<String, String> map = objectMapper.readValue(json, new TypeReference<>() {
            });
            return map.get("content");
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    /** 生成会话列表中的最后消息预览。 */
    private String preview(String content) {
        if (content.length() <= 60) {
            return content;
        }
        return content.substring(0, 60);
    }

    /** 格式化时间；空值保持为空。 */
    private String format(LocalDateTime time) {
        return time == null ? null : FORMATTER.format(time);
    }
}
