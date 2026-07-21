package com.tongluxing.chat.service.impl;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.chat.dto.SendMessageRequest;
import com.tongluxing.chat.dto.TeamConversationRequest;
import com.tongluxing.chat.entity.ChatConversation;
import com.tongluxing.chat.entity.ChatConversationMember;
import com.tongluxing.chat.entity.ChatMessage;
import com.tongluxing.chat.mapper.ChatConversationMapper;
import com.tongluxing.chat.mapper.ChatConversationMemberMapper;
import com.tongluxing.chat.mapper.ChatMessageMapper;
import com.tongluxing.chat.mapper.MessageRiskMapper;
import com.tongluxing.chat.mapper.ChatJoinApplicationMapper;
import com.tongluxing.chat.entity.ChatJoinApplication;
import com.tongluxing.chat.entity.MessageRisk;
import com.tongluxing.chat.service.ChatService;
import com.tongluxing.chat.service.TencentImService;
import com.tongluxing.chat.vo.ConversationListResponse;
import com.tongluxing.chat.vo.ConversationMemberResponse;
import com.tongluxing.chat.vo.ConversationResponse;
import com.tongluxing.chat.vo.MessageListResponse;
import com.tongluxing.chat.vo.MessageResponse;
import com.tongluxing.chat.vo.ConversationSettingResponse;
import com.tongluxing.chat.vo.JoinApplicationResponse;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.user.support.CurrentUserContext;
import com.tongluxing.user.service.UserService;
import com.tongluxing.user.model.UserModels.PublicProfileVO;
import com.tongluxing.trip.service.TripService;
import com.tongluxing.trip.vo.TripResponse;

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
    /** 风险命中记录，仅进入受控审核队列。 */
    private final MessageRiskMapper riskMapper;
    /** 入群申请事实表。 */
    private final ChatJoinApplicationMapper joinApplicationMapper;
    /** 只读取公开资料，用于成员和申请人展示。 */
    private final UserService userService;
    /** 用于兼容升级前已发布行程：首次进入时惰性补建行程群。 */
    private final TripService tripService;

    /** 创建或复用车队群聊会话。 */
    @Override
    @Transactional
    public ConversationResponse createTeamConversation(TeamConversationRequest request) {
        ChatConversation existed = conversationMapper.findByBiz("TEAM", request.teamId());
        if (existed != null) {
            return toConversationResponse(existed);
        }
        LocalDateTime now = LocalDateTime.now();
        ChatConversation conversation = new ChatConversation();
        conversation.setId(SnowflakeIdGenerator.nextId());
        String groupId = groupId(request.teamId());
        boolean cloudEnabled = tencentImService.isConfigured();
        if (cloudEnabled) {
            tencentImService.createGroup(groupId, TencentImServiceImpl.toImUserId(request.ownerUserId()), request.conversationName());
        }
        conversation.setBizType("TEAM");
        conversation.setBizId(request.teamId());
        conversation.setConversationName(request.conversationName());
        conversation.setConversationStatus("ACTIVE");
        conversation.setProviderType(cloudEnabled ? "TENCENT_IM" : "LOCAL");
        conversation.setProviderConversationKey(groupId);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        addMemberInternal(conversation.getId(), request.ownerUserId(), "OWNER");
        return toConversationResponse(conversation);
    }

    /** 行程开启后自动创建群聊，并把车主和已通过成员加入群聊。 */
    @Override
    @Transactional
    public ConversationResponse openTripConversation(Long tripId, String tripName, Long ownerUserId, List<Long> memberUserIds) {
        ChatConversation existed = conversationMapper.findByBiz("TRIP", tripId);
        if (existed != null) {
            syncTripMembers(existed, ownerUserId, memberUserIds);
            persistSystemMessage(existed.getId(), "行程已开启，请注意行车安全");
            return toConversationResponse(conversationMapper.findById(existed.getId()));
        }
        return createTripConversation(tripId, tripName, ownerUserId, memberUserIds, "行程已开启，群聊已创建，请注意行车安全");
    }

    /** 发布后即建立行程群，确保开启前的成员确认有可用会话承载。 */
    @Override
    @Transactional
    public ConversationResponse prepareTripConversation(Long tripId, String tripName, Long ownerUserId, List<Long> memberUserIds) {
        ChatConversation existed = conversationMapper.findByBiz("TRIP", tripId);
        if (existed != null) {
            syncTripMembers(existed, ownerUserId, memberUserIds);
            return toConversationResponse(existed);
        }
        return createTripConversation(tripId, tripName, ownerUserId, memberUserIds, "行程已发布，可在群内沟通并完成出发确认");
    }

    private ConversationResponse createTripConversation(Long tripId, String tripName, Long ownerUserId,
                                                        List<Long> memberUserIds, String initialMessage) {
        LocalDateTime now = LocalDateTime.now();
        String groupId = "trip_" + tripId;
        boolean cloudEnabled = tencentImService.isConfigured();
        String name = StringUtils.hasText(tripName) ? tripName.trim() + " · 行程群" : "行程车队群";
        if (cloudEnabled) {
            tencentImService.createGroup(groupId, TencentImServiceImpl.toImUserId(ownerUserId), name);
        }
        ChatConversation conversation = new ChatConversation();
        conversation.setId(SnowflakeIdGenerator.nextId());
        conversation.setBizType("TRIP");
        conversation.setBizId(tripId);
        conversation.setConversationName(name);
        conversation.setConversationStatus("ACTIVE");
        conversation.setProviderType(cloudEnabled ? "TENCENT_IM" : "LOCAL");
        conversation.setProviderConversationKey(groupId);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);

        syncTripMembers(conversation, ownerUserId, memberUserIds);
        persistSystemMessage(conversation.getId(), initialMessage);
        return toConversationResponse(conversationMapper.findById(conversation.getId()));
    }

    private void syncTripMembers(ChatConversation conversation, Long ownerUserId, List<Long> memberUserIds) {
        LinkedHashSet<Long> users = new LinkedHashSet<>();
        users.add(ownerUserId);
        if (memberUserIds != null) {
            users.addAll(memberUserIds);
        }
        for (Long userId : users) {
            if (userId == null) {
                continue;
            }
            ChatConversationMember before = memberMapper.findByConversationAndUser(conversation.getId(), userId);
            boolean needsCloudSync = before == null || !"ACTIVE".equals(before.getMemberStatus());
            addMemberInternal(conversation.getId(), userId, userId.equals(ownerUserId) ? "OWNER" : "MEMBER");
            if (needsCloudSync && "TENCENT_IM".equals(conversation.getProviderType()) && tencentImService.isConfigured()
                    && !userId.equals(ownerUserId)) {
                tencentImService.addGroupMember(conversation.getProviderConversationKey(), TencentImServiceImpl.toImUserId(userId));
            }
        }
    }

    /** 只允许当前有效群成员按行程 ID 获取会话。 */
    @Override
    public ConversationResponse getTripConversation(Long tripId) {
        Long userId = currentUserContext.requireUserId();
        ChatConversation conversation = conversationMapper.findByBiz("TRIP", tripId);
        if (conversation == null) {
            TripResponse trip = tripService.getTrip(tripId);
            List<Long> tripMembers = tripService.getMembers(tripId).stream()
                    .filter(member -> "OWNER".equals(member.joinStatus()) || "APPROVED".equals(member.joinStatus()))
                    .map(member -> Long.valueOf(member.userId()))
                    .distinct()
                    .toList();
            Long ownerId = Long.valueOf(trip.userId());
            if (List.of("RUNNING", "ONGOING").contains(trip.status())) {
                openTripConversation(tripId, trip.title(), ownerId, tripMembers);
            } else {
                prepareTripConversation(tripId, trip.title(), ownerId, tripMembers);
            }
            conversation = conversationMapper.findByBiz("TRIP", tripId);
        }
        if (conversation == null || !"ACTIVE".equals(conversation.getConversationStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程群聊尚未创建或已结束");
        }
        requireActiveMember(conversation.getId(), userId);
        return toConversationResponse(conversation);
    }

    /** 行程结束后先保留结束通知，再退出成员、归档本地会话并按配置销毁云群组。 */
    @Override
    @Transactional
    public void closeTripConversation(Long tripId) {
        ChatConversation conversation = conversationMapper.findByBiz("TRIP", tripId);
        if (conversation == null || !"ACTIVE".equals(conversation.getConversationStatus())) {
            return;
        }
        persistSystemMessage(conversation.getId(), "行程已结束，群聊已归档，历史风险记录将按安全策略保留");
        if ("TENCENT_IM".equals(conversation.getProviderType()) && tencentImService.isConfigured()) {
            tencentImService.destroyGroup(conversation.getProviderConversationKey());
        }
        LocalDateTime now = LocalDateTime.now();
        memberMapper.exitAll(conversation.getId(), now);
        conversationMapper.archive(conversation.getId(), now);
    }

    /** 查询当前用户参与的有效会话列表。 */
    @Override
    public ConversationListResponse getConversations() {
        Long userId = currentUserContext.requireUserId();
        return new ConversationListResponse(conversationMapper.findActiveByUserId(userId).stream()
                .map(conversation -> toConversationResponse(conversation,
                        memberMapper.findByConversationAndUser(conversation.getId(), userId)))
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
    @Transactional
    public MessageResponse sendMessage(Long conversationId, SendMessageRequest request) {
        Long userId = currentUserContext.requireUserId();
        requireActiveMember(conversationId, userId);
        ChatConversation conversation = conversationMapper.findById(conversationId);
        if (conversation == null || !"ACTIVE".equals(conversation.getConversationStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在或已归档");
        }
        if (!StringUtils.hasText(request.content())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "消息内容不能为空");
        }
        if ("FILE".equalsIgnoreCase(request.messageType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前群聊仅支持发送图片，暂不支持普通文件");
        }
        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = new ChatMessage();
        message.setId(SnowflakeIdGenerator.nextId());
        message.setConversationId(conversationId);
        message.setSenderUserId(userId);
        message.setMessageType(request.messageType());
        Map<String, Object> messagePayload = new java.util.LinkedHashMap<>();
        messagePayload.put("content", request.content());
        if (request.payload() != null) messagePayload.putAll(request.payload());
        message.setMessagePayloadJson(toJson(messagePayload));
        RiskDecision risk = detectRisk(request.content());
        message.setMessageStatus(risk == null ? "NORMAL" : (risk.blocked() ? "BLOCKED" : "RISK_REVIEW"));
        message.setProviderMessageKey("local-msg-" + message.getId());
        message.setSentAt(now);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        if ((risk == null || !risk.blocked())
                && "TENCENT_IM".equals(conversation.getProviderType()) && tencentImService.isConfigured()) {
            message.setProviderMessageKey(tencentImService.sendGroupText(
                    conversation.getProviderConversationKey(),
                    TencentImServiceImpl.toImUserId(userId),
                    request.content()
            ));
        }
        messageMapper.insert(message);
        if (risk != null) {
            persistRisk(message.getId(), risk, now);
        }
        if (risk != null && risk.blocked()) {
            return toMessageResponse(message);
        }
        conversationMapper.updateLastMessage(conversationId, message.getId(), preview(request.content()), now);
        // 给除发送者之外的有效成员增加未读数。
        memberMapper.incrementUnread(conversationId, userId, now);
        return toMessageResponse(message);
    }

    /** 添加会话成员，并同步到腾讯云 IM 群组。 */
    @Override
    @Transactional
    public ConversationMemberResponse addMember(Long conversationId, Long userId) {
        ChatConversationMember operator = requireActiveMember(conversationId, currentUserContext.requireUserId());
        if (!"OWNER".equals(operator.getMemberRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有队长可以添加群成员");
        }
        ChatConversation conversation = conversationMapper.findById(conversationId);
        if (conversation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        if ("TENCENT_IM".equals(conversation.getProviderType()) && tencentImService.isConfigured()
                && StringUtils.hasText(conversation.getProviderConversationKey())) {
            tencentImService.addGroupMember(conversation.getProviderConversationKey(), TencentImServiceImpl.toImUserId(userId));
        }
        return toMemberResponse(addMemberInternal(conversationId, userId, "MEMBER"));
    }

    /** 当前用户退出会话，并同步移除腾讯云 IM 群成员。 */
    @Override
    @Transactional
    public void exitMe(Long conversationId) {
        Long userId = currentUserContext.requireUserId();
        ChatConversationMember member = requireActiveMember(conversationId, userId);
        if ("OWNER".equals(member.getMemberRole())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "队长不能直接退出群聊");
        }
        ChatConversation conversation = conversationMapper.findById(conversationId);
        if (conversation != null && "TENCENT_IM".equals(conversation.getProviderType()) && tencentImService.isConfigured()
                && StringUtils.hasText(conversation.getProviderConversationKey())) {
            tencentImService.removeGroupMember(conversation.getProviderConversationKey(), TencentImServiceImpl.toImUserId(userId));
        }
        int changed = memberMapper.exit(conversationId, userId, LocalDateTime.now());
        if (changed == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权退出该会话");
        }
    }

    @Override
    public List<ConversationMemberResponse> getMembers(Long conversationId) {
        Long userId = currentUserContext.requireUserId();
        requireActiveMember(conversationId, userId);
        return memberMapper.findActiveByConversationId(conversationId).stream()
                .map(this::toMemberResponse).toList();
    }

    @Override
    public ConversationSettingResponse getSettings(Long conversationId) {
        ChatConversationMember member = requireActiveMember(conversationId, currentUserContext.requireUserId());
        return toSettingResponse(member);
    }

    @Override
    public ConversationSettingResponse updateSettings(Long conversationId, boolean muted, boolean pinned) {
        Long userId = currentUserContext.requireUserId();
        requireActiveMember(conversationId, userId);
        memberMapper.updateSettings(conversationId, userId, muted, pinned, LocalDateTime.now());
        return new ConversationSettingResponse(String.valueOf(conversationId), muted, pinned);
    }

    @Override
    @Transactional
    public JoinApplicationResponse applyToJoin(Long conversationId, String message) {
        Long userId = currentUserContext.requireUserId();
        ChatConversation conversation = conversationMapper.findById(conversationId);
        if (conversation == null || !"ACTIVE".equals(conversation.getConversationStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "群聊不存在或已结束");
        }
        ChatConversationMember member = memberMapper.findByConversationAndUser(conversationId, userId);
        if (member != null && "ACTIVE".equals(member.getMemberStatus())) {
            throw new BusinessException(409, "你已经是群成员");
        }
        if (joinApplicationMapper.findPending(conversationId, userId) != null) {
            throw new BusinessException(409, "你已有待审核的入群申请");
        }
        LocalDateTime now = LocalDateTime.now();
        ChatJoinApplication row = new ChatJoinApplication();
        row.setId(SnowflakeIdGenerator.nextId());
        row.setConversationId(conversationId);
        row.setApplicantUserId(userId);
        row.setApplicationMessage(message == null ? "" : message.trim());
        row.setApplicationStatus("PENDING");
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        joinApplicationMapper.insert(row);
        return toJoinApplicationResponse(row);
    }

    @Override
    public List<JoinApplicationResponse> getJoinApplications(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : "PENDING";
        if (!List.of("PENDING", "APPROVED", "REJECTED").contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "申请状态不合法");
        }
        return joinApplicationMapper.findOwnerQueue(currentUserContext.requireUserId(), normalized)
                .stream().map(this::toJoinApplicationResponse).toList();
    }

    @Override
    @Transactional
    public JoinApplicationResponse reviewJoinApplication(Long applicationId, String decision) {
        Long reviewerId = currentUserContext.requireUserId();
        ChatJoinApplication row = joinApplicationMapper.findById(applicationId);
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "入群申请不存在");
        }
        ChatConversationMember owner = requireActiveMember(row.getConversationId(), reviewerId);
        if (!"OWNER".equals(owner.getMemberRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有队长可以审核入群申请");
        }
        String normalized = decision.trim().toUpperCase();
        if (joinApplicationMapper.review(applicationId, normalized, reviewerId, LocalDateTime.now()) != 1) {
            throw new BusinessException(409, "该申请已经处理");
        }
        if ("APPROVED".equals(normalized)) {
            addMember(row.getConversationId(), row.getApplicantUserId());
            persistSystemMessage(row.getConversationId(), publicName(row.getApplicantUserId()) + " 已加入群聊");
        }
        row.setApplicationStatus(normalized);
        return toJoinApplicationResponse(row);
    }

    /** 生成车队群聊在腾讯云 IM 中的群组 ID。 */
    private String groupId(Long teamId) {
        return "team_" + teamId;
    }

    /** 写入行程生命周期系统消息。 */
    private void persistSystemMessage(Long conversationId, String content) {
        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = new ChatMessage();
        message.setId(SnowflakeIdGenerator.nextId());
        message.setConversationId(conversationId);
        message.setSenderUserId(null);
        message.setMessageType("SYSTEM");
        message.setMessagePayloadJson(toJson(Map.of("content", content)));
        message.setMessageStatus("NORMAL");
        message.setProviderMessageKey("system-" + message.getId());
        message.setSentAt(now);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        messageMapper.insert(message);
        conversationMapper.updateLastMessage(conversationId, message.getId(), content, now);
    }

    /** 保存风险命中事实，后续管理端只能通过风险审核权限访问。 */
    private void persistRisk(Long messageId, RiskDecision decision, LocalDateTime now) {
        MessageRisk risk = new MessageRisk();
        risk.setId(SnowflakeIdGenerator.nextId());
        risk.setMessageId(messageId);
        risk.setRiskLevel(decision.level());
        risk.setRiskType(decision.type());
        risk.setConfidence(decision.confidence());
        risk.setStatus("PENDING");
        risk.setMatchedRule(decision.rule());
        risk.setCreatedAt(now);
        risk.setUpdatedAt(now);
        riskMapper.insert(risk);
    }

    /** 第一阶段本地规则：高风险拦截，中低风险正常送达并进入审核队列。 */
    private RiskDecision detectRisk(String content) {
        String text = content.toLowerCase();
        if (containsAny(text, "转账", "银行卡", "验证码", "保证金")) {
            return new RiskDecision("HIGH", "FRAUD", 95, "fraud-keyword", true);
        }
        if (containsAny(text, "毒品", "枪支", "买枪")) {
            return new RiskDecision("HIGH", "ILLEGAL", 98, "illegal-keyword", true);
        }
        if (containsAny(text, "加微信", "兼职", "扫码", "返利")) {
            return new RiskDecision("MEDIUM", "AD", 82, "ad-keyword", false);
        }
        if (containsAny(text, "傻逼", "垃圾", "滚开")) {
            return new RiskDecision("MEDIUM", "ABUSE", 88, "abuse-keyword", false);
        }
        if (containsAny(text, "色情", "裸聊")) {
            return new RiskDecision("MEDIUM", "SEX", 90, "sex-keyword", false);
        }
        return null;
    }

    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private record RiskDecision(String level, String type, int confidence, String rule, boolean blocked) {
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
        member.setMutedFlag(false);
        member.setPinnedFlag(false);
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
        return toConversationResponse(conversation, null);
    }

    private ConversationResponse toConversationResponse(ChatConversation conversation, ChatConversationMember member) {
        return new ConversationResponse(
                String.valueOf(conversation.getId()),
                conversation.getBizType(),
                String.valueOf(conversation.getBizId()),
                conversation.getConversationName(),
                conversation.getConversationStatus(),
                conversation.getProviderType(),
                conversation.getLastMessagePreview(),
                format(conversation.getLastMessageAt()),
                member != null && Boolean.TRUE.equals(member.getPinnedFlag()),
                member != null && Boolean.TRUE.equals(member.getMutedFlag())
        );
    }

    /** 转换会话成员响应。 */
    private ConversationMemberResponse toMemberResponse(ChatConversationMember member) {
        PublicProfileVO profile = safePublicProfile(member.getUserId());
        return new ConversationMemberResponse(
                String.valueOf(member.getId()),
                String.valueOf(member.getConversationId()),
                String.valueOf(member.getUserId()),
                member.getMemberRole(),
                member.getMemberStatus(),
                member.getUnreadCount(),
                profile.nickname(),
                profile.avatarImageKey(),
                profile.totalTripCount(),
                profile.totalDistanceMeters(),
                profile.completedWaypointCount(),
                Boolean.TRUE.equals(member.getMutedFlag()),
                Boolean.TRUE.equals(member.getPinnedFlag()),
                format(member.getJoinedAt())
        );
    }

    private ConversationSettingResponse toSettingResponse(ChatConversationMember member) {
        return new ConversationSettingResponse(String.valueOf(member.getConversationId()),
                Boolean.TRUE.equals(member.getMutedFlag()), Boolean.TRUE.equals(member.getPinnedFlag()));
    }

    private JoinApplicationResponse toJoinApplicationResponse(ChatJoinApplication row) {
        PublicProfileVO profile = safePublicProfile(row.getApplicantUserId());
        ChatConversation conversation = conversationMapper.findById(row.getConversationId());
        return new JoinApplicationResponse(String.valueOf(row.getId()), String.valueOf(row.getConversationId()),
                conversation == null ? "车队群聊" : conversation.getConversationName(),
                String.valueOf(row.getApplicantUserId()), profile.nickname(), profile.avatarImageKey(),
                row.getApplicationMessage(), row.getApplicationStatus(), format(row.getCreatedAt()));
    }

    private String publicName(Long userId) {
        String name = safePublicProfile(userId).nickname();
        return StringUtils.hasText(name) ? name : "新成员";
    }

    private PublicProfileVO safePublicProfile(Long userId) {
        try {
            PublicProfileVO profile = userService.getPublicProfile(userId);
            String nickname = StringUtils.hasText(profile.nickname()) ? profile.nickname() : "同路行用户";
            return new PublicProfileVO(profile.userId(), nickname, profile.avatarImageKey(), profile.cityName(),
                    profile.bio(), profile.drivingLicenseCertificationStatus(), profile.totalTripCount(),
                    profile.totalDistanceMeters(), profile.totalDurationMinutes(), profile.completedWaypointCount());
        } catch (RuntimeException ignored) {
            return new PublicProfileVO(userId, "同路行用户", "", "", "一起安全出发",
                    "UNSUBMITTED");
        }
    }

    /** 转换消息响应。 */
    private MessageResponse toMessageResponse(ChatMessage message) {
        PublicProfileVO sender = message.getSenderUserId() == null ? null : safePublicProfile(message.getSenderUserId());
        return new MessageResponse(
                String.valueOf(message.getId()),
                String.valueOf(message.getConversationId()),
                message.getSenderUserId() == null ? null : String.valueOf(message.getSenderUserId()),
                message.getMessageType(),
                readContent(message.getMessagePayloadJson()),
                sender == null ? null : sender.nickname(),
                sender == null ? null : sender.avatarImageKey(),
                readPayload(message.getMessagePayloadJson()),
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

    private Map<String, Object> readPayload(String value) {
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            return Map.of();
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
