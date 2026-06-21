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

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ChatConversationMapper conversationMapper;
    private final ChatConversationMemberMapper memberMapper;
    private final ChatMessageMapper messageMapper;
    private final CurrentUserContext currentUserContext;
    private final ObjectMapper objectMapper;
    private final TencentImService tencentImService;

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

    @Override
    public ConversationListResponse getConversations() {
        Long userId = currentUserContext.requireUserId();
        return new ConversationListResponse(conversationMapper.findActiveByUserId(userId).stream()
                .map(this::toConversationResponse)
                .toList());
    }

    @Override
    public MessageListResponse getMessages(Long conversationId, Long beforeMessageId, Integer limit) {
        Long userId = currentUserContext.requireUserId();
        requireActiveMember(conversationId, userId);
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        return new MessageListResponse(messageMapper.findMessages(conversationId, beforeMessageId, safeLimit).stream()
                .map(this::toMessageResponse)
                .toList());
    }

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
        memberMapper.incrementUnread(conversationId, userId, now);
        return toMessageResponse(message);
    }

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

    private String groupId(Long teamId) {
        return "team_" + teamId;
    }

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

    private ChatConversationMember requireActiveMember(Long conversationId, Long userId) {
        ChatConversationMember member = memberMapper.findByConversationAndUser(conversationId, userId);
        if (member == null || !"ACTIVE".equals(member.getMemberStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权访问该会话");
        }
        return member;
    }

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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "消息序列化失败");
        }
    }

    private String readContent(String json) {
        try {
            Map<String, String> map = objectMapper.readValue(json, new TypeReference<>() {
            });
            return map.get("content");
        } catch (JsonProcessingException exception) {
            return "";
        }
    }

    private String preview(String content) {
        if (content.length() <= 60) {
            return content;
        }
        return content.substring(0, 60);
    }

    private String format(LocalDateTime time) {
        return time == null ? null : FORMATTER.format(time);
    }
}
