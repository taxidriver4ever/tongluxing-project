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
import com.tongluxing.chat.vo.PrivateChatPermissionResponse;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.user.support.CurrentUserContext;
import com.tongluxing.user.service.UserService;
import com.tongluxing.user.vo.PublicProfileVO;
import com.tongluxing.trip.service.TripService;
import com.tongluxing.trip.vo.TripResponse;
import com.tongluxing.team.service.TeamService;
import com.tongluxing.team.vo.TeamResponse;
import com.tongluxing.storage.service.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 聊天业务服务实现。
 *
 * <p>本类维护同路行自己的业务绑定、成员权限、举报与兼容消息表。
 * App 的会话列表、未读数、置顶、免打扰、消息历史和实时收发由腾讯 IM SDK 负责；
 * 本地数据库不再作为 App 普通聊天记录的读取来源。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
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
    /** 读取聊天成员基础资料，用于消息、成员和申请人展示。 */
    private final UserService userService;
    /** 用于兼容升级前已发布行程：首次进入时惰性补建行程群。 */
    private final TripService tripService;
    /** 退群时同步退出关联车队和行程，释放成员名额。 */
    private final TeamService teamService;
    /** 为聊天头像生成短期有效的 MinIO 访问地址。 */
    private final StorageService storageService;

    /** 创建或复用车队群聊会话。 */
    @Override
    @Transactional
    public ConversationResponse createTeamConversation(TeamConversationRequest request) {
        Long currentUserId = currentUserContext.requireUserId();

        // 第一步：从 team-module 读取真实车队信息，绝不信任客户端提交的群主身份。
        TeamResponse team = teamService.getTeam(request.teamId());
        if (!String.valueOf(currentUserId).equals(team.ownerUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有车队队长可以创建车队群");
        }
        if (!"ACTIVE".equals(team.teamStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "当前车队状态不允许创建群聊");
        }

        // 第二步：业务维度（TEAM + teamId）保持唯一，接口重试时直接复用已有群。
        ChatConversation existed = conversationMapper.findByBiz("TEAM", request.teamId());
        if (existed != null) {
            return toConversationResponse(ensureTencentProvider(existed),
                    memberMapper.findByConversationAndUser(existed.getId(), currentUserId));
        }

        LocalDateTime now = LocalDateTime.now();
        ChatConversation conversation = new ChatConversation();
        conversation.setId(SnowflakeIdGenerator.nextId());
        String groupId = groupId(request.teamId());
        // 第三步：云端群主固定为当前已校验的队长，不能由请求体指定。
        // MOCK 已移除，因此建群必须成功同步到真实腾讯 IM。
        tencentImService.createGroup(groupId, TencentImServiceImpl.toImUserId(currentUserId),
                request.conversationName().trim());

        // 第四步：保存同路行的业务绑定；消息、未读和置顶由腾讯 IM 托管。
        conversation.setBizType("TEAM");
        conversation.setBizId(request.teamId());
        conversation.setConversationName(request.conversationName().trim());
        conversation.setConversationStatus("ACTIVE");
        conversation.setProviderType("TENCENT_IM");
        conversation.setProviderConversationKey(groupId);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        ChatConversationMember owner = addMemberInternal(conversation.getId(), currentUserId, "OWNER");
        return toConversationResponse(conversation, owner);
    }

    /** 行程开启后自动创建群聊，并把车主和已通过成员加入群聊。 */
    @Override
    @Transactional
    public ConversationResponse openTripConversation(Long tripId, String tripName, Long ownerUserId, List<Long> memberUserIds) {
        // 开始行程可能因接口重试被重复触发，因此整个方法按行程业务键保持幂等。
        ChatConversation existed = conversationMapper.findByBiz("TRIP", tripId);
        if (existed != null) {
            // 遗留会话先幂等补建腾讯群，再同步本次新增成员，避免向不存在的云端群加人。
            existed = ensureTencentProvider(existed);
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
            // 遗留会话先完成腾讯 IM 迁移，再同步行程当前有效成员。
            existed = ensureTencentProvider(existed);
            syncTripMembers(existed, ownerUserId, memberUserIds);
            return toConversationResponse(existed);
        }
        return createTripConversation(tripId, tripName, ownerUserId, memberUserIds, "行程已发布，可在群内沟通并完成出发确认");
    }

    private ConversationResponse createTripConversation(Long tripId, String tripName, Long ownerUserId,
                                                        List<Long> memberUserIds, String initialMessage) {
        // trip_{tripId} 是稳定的云端 GroupId，服务重启或迁移重试不会产生多个群。
        LocalDateTime now = LocalDateTime.now();
        String groupId = "trip_" + tripId;
        String name = StringUtils.hasText(tripName) ? tripName.trim() + " · 行程群" : "行程车队群";
        // 所有环境都使用真实腾讯 IM；创建失败时直接中止本次业务事务。
        tencentImService.createGroup(groupId, TencentImServiceImpl.toImUserId(ownerUserId), name);
        ChatConversation conversation = new ChatConversation();
        conversation.setId(SnowflakeIdGenerator.nextId());
        conversation.setBizType("TRIP");
        conversation.setBizId(tripId);
        conversation.setConversationName(name);
        conversation.setConversationStatus("ACTIVE");
        conversation.setProviderType("TENCENT_IM");
        conversation.setProviderConversationKey(groupId);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);

        // 会话主记录落库后再同步成员和系统消息；外层事务保证任一步失败时本地数据整体回滚。
        syncTripMembers(conversation, ownerUserId, memberUserIds);
        persistSystemMessage(conversation.getId(), initialMessage);
        return toConversationResponse(conversationMapper.findById(conversation.getId()));
    }

    private void syncTripMembers(ChatConversation conversation, Long ownerUserId, List<Long> memberUserIds) {
        // LinkedHashSet 同时完成去重并保留 owner 在首位，避免成员列表中重复写入同一用户。
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
            // 仅新增或重新激活的成员需要调用云端加群接口，减少无意义的远程请求。
            boolean needsCloudSync = before == null || !"ACTIVE".equals(before.getMemberStatus());
            addMemberInternal(conversation.getId(), userId, userId.equals(ownerUserId) ? "OWNER" : "MEMBER");
            if (needsCloudSync && !userId.equals(ownerUserId)) {
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
            // 兼容升级前已发布但没有群聊的数据：首次访问时从行程事实表恢复群及有效成员。
            TripResponse trip = tripService.getTrip(tripId);
            List<Long> tripMembers = tripService.getMembers(tripId).stream()
                    .filter(member -> "OWNER".equals(member.joinStatus()) || "APPROVED".equals(member.joinStatus()))
                    .map(member -> Long.valueOf(member.userId()))
                    .distinct()
                    .toList();
            Long ownerId = Long.valueOf(trip.userId());
            // 已开始的行程补建时使用“开启”文案，其余可聊天状态使用“已发布”文案。
            if (List.of("RUNNING", "ONGOING").contains(trip.status())) {
                openTripConversation(tripId, trip.title(), ownerId, tripMembers);
            } else {
                prepareTripConversation(tripId, trip.title(), ownerId, tripMembers);
            }
            conversation = conversationMapper.findByBiz("TRIP", tripId);
        }
        if (conversation == null || !List.of("ACTIVE", "HISTORY").contains(conversation.getConversationStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "行程群聊尚未创建或已结束");
        }
        conversation = ensureTencentProvider(conversation);
        requireActiveMember(conversation.getId(), userId);
        return toConversationResponse(conversation);
    }

    /** 行程结束后转为历史群：保留成员、云群组和全部消息，只关闭实时行程能力。 */
    @Override
    @Transactional
    public void closeTripConversation(Long tripId) {
        ChatConversation conversation = conversationMapper.findByBiz("TRIP", tripId);
        if (conversation == null || !"ACTIVE".equals(conversation.getConversationStatus())) {
            return;
        }
        conversation = ensureTencentProvider(conversation);
        persistSystemMessage(conversation.getId(), "该行程已结束，当前为历史车队群；仍可继续交流和查看历史消息");
        LocalDateTime now = LocalDateTime.now();
        conversationMapper.markHistory(conversation.getId(), now);
    }

    /** 查询当前用户参与的有效会话列表。 */
    @Override
    @Transactional
    public ConversationListResponse getConversations(String title) {
        Long userId = currentUserContext.requireUserId();
        // 空白关键词统一转为 null，让 Mapper 跳过 LIKE 条件并复用普通查询计划。
        String titleKeyword = StringUtils.hasText(title) ? title.trim() : null;
        return new ConversationListResponse(conversationMapper.findActiveByUserId(userId, titleKeyword).stream()
                .map(this::ensureTencentProvider)
                .map(conversation -> toConversationResponse(conversation,
                        memberMapper.findByConversationAndUser(conversation.getId(), userId)))
                .toList());
    }

    /**
     * 返回腾讯 IM 会话与同路行业务对象的绑定信息。
     *
     * <p>这里只返回当前用户有权访问的本地业务元数据；App 会用 imConversationId
     * 与腾讯 IM SDK 的会话列表做合并，SDK 数据决定排序、未读、最后消息、置顶和免打扰。</p>
     */
    @Override
    @Transactional
    public ConversationListResponse getImConversationBindings() {
        Long userId = currentUserContext.requireUserId();
        List<ConversationResponse> bindings = conversationMapper.findActiveByUserId(userId, null).stream()
                .map(this::ensureTencentProvider)
                .map(conversation -> toConversationResponse(conversation,
                        memberMapper.findByConversationAndUser(conversation.getId(), userId)))
                .toList();
        return new ConversationListResponse(bindings);
    }

    /** 检查当前用户能否与目标用户发起私聊，以及未解锁时剩余的文字消息额度。 */
    @Override
    public PrivateChatPermissionResponse getPrivatePermission(Long targetUserId) {
        Long currentUserId = currentUserContext.requireUserId();
        validatePrivateTarget(currentUserId, targetUserId);
        ChatConversation existing = conversationMapper.findByProviderKey("PRIVATE", privatePairKey(currentUserId, targetUserId));
        return privatePermission(currentUserId, targetUserId, existing);
    }

    /** 创建或恢复两名用户之间唯一的私聊会话。 */
    @Override
    @Transactional
    public ConversationResponse startPrivateConversation(Long targetUserId) {
        Long currentUserId = currentUserContext.requireUserId();
        validatePrivateTarget(currentUserId, targetUserId);
        String pairKey = privatePairKey(currentUserId, targetUserId);
        // pairKey 对用户 ID 排序，因此 A 找 B 与 B 找 A 始终命中同一会话。
        ChatConversation existing = conversationMapper.findByProviderKey("PRIVATE", pairKey);
        if (existing != null) {
            // 曾退出或被隐藏的私聊成员在再次发起时重新激活，同时保留原有角色语义。
            addMemberInternal(existing.getId(), currentUserId, existingPrivateRole(existing.getId(), currentUserId, "OWNER"));
            addMemberInternal(existing.getId(), targetUserId, existingPrivateRole(existing.getId(), targetUserId, "MEMBER"));
            existing = ensureTencentProvider(existing);
            return toConversationResponse(existing,
                    memberMapper.findByConversationAndUser(existing.getId(), currentUserId));
        }
        PrivateChatPermissionResponse permission = privatePermission(currentUserId, targetUserId, null);
        if (!permission.canStart()) {
            throw new BusinessException(ResultCode.FORBIDDEN,
                    StringUtils.hasText(permission.reason()) ? permission.reason() : "关注对方后即可发起私聊");
        }
        // 私聊不创建腾讯云群，云端消息在发送时通过 C2C 接口按双方账号投递。
        LocalDateTime now = LocalDateTime.now();
        ChatConversation conversation = new ChatConversation();
        conversation.setId(SnowflakeIdGenerator.nextId());
        conversation.setBizType("PRIVATE");
        conversation.setBizId(conversation.getId());
        conversation.setConversationName("私聊");
        conversation.setConversationStatus("ACTIVE");
        conversation.setProviderType("TENCENT_IM");
        conversation.setProviderConversationKey(pairKey);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);
        ChatConversationMember mine = addMemberInternal(conversation.getId(), currentUserId, "OWNER");
        addMemberInternal(conversation.getId(), targetUserId, "MEMBER");
        return toConversationResponse(conversation, mine);
    }

    /** 查询既有私聊的发送权限；先校验会话成员身份，防止枚举会话 ID。 */
    @Override
    public PrivateChatPermissionResponse getPrivateConversationPermission(Long conversationId) {
        Long currentUserId = currentUserContext.requireUserId();
        requireActiveMember(conversationId, currentUserId);
        ChatConversation conversation = conversationMapper.findById(conversationId);
        if (conversation == null || !"PRIVATE".equals(conversation.getBizType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该会话不是私聊");
        }
        ChatConversationMember peer = memberMapper.findOtherActive(conversationId, currentUserId);
        if (peer == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "私聊对象不存在");
        }
        return privatePermission(currentUserId, peer.getUserId(), conversation);
    }

    /** 查询会话历史消息；仅允许有效成员访问。 */
    @Override
    public MessageListResponse getMessages(Long conversationId, Long beforeMessageId, Integer limit) {
        Long userId = currentUserContext.requireUserId();
        ChatConversationMember member = requireActiveMember(conversationId, userId);
        // 限制分页大小，防止一次拉取过多消息影响数据库性能。
        int safeLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 100));
        List<ChatMessage> messages = messageMapper.findMessages(conversationId, beforeMessageId,
                null, safeLimit);
        if (beforeMessageId == null) {
            // 只有读取最新一页才推进已读游标；向上翻历史消息不应误清除新消息未读数。
            Long latest = messageMapper.findLatestMessageId(conversationId);
            LocalDateTime now = LocalDateTime.now();
            memberMapper.markRead(conversationId, userId, latest, now);
            // 从个人资料或行程入口重新进入聊天，即视为主动恢复该会话。
            memberMapper.restoreVisibility(conversationId, userId, now);
        }
        return new MessageListResponse(messages.stream().map(this::toMessageResponse).toList());
    }

    /** 从当前用户的会话列表隐藏会话；新消息到达后会自动重新出现。 */
    @Override
    @Transactional
    public void hideConversation(Long conversationId) {
        Long userId = currentUserContext.requireUserId();
        requireActiveMember(conversationId, userId);
        Long latest = messageMapper.findLatestMessageId(conversationId);
        // 复用历史字段作为“隐藏到哪条消息”的游标。消息查询不再使用该游标，
        // 因此记录始终保留；只有会话列表会在出现更新消息前暂时隐藏。
        memberMapper.clearLocalMessages(conversationId, userId, latest == null ? 0L : latest, LocalDateTime.now());
    }

    /** 行程资料被群主修改后写入系统提示，并让其他成员看到未读提醒。 */
    @Override
    @Transactional
    public void notifyTripUpdated(Long tripId, Long operatorUserId) {
        ChatConversation conversation = conversationMapper.findByBiz("TRIP", tripId);
        if (conversation == null || !List.of("ACTIVE", "HISTORY").contains(conversation.getConversationStatus())) {
            return;
        }
        ChatConversationMember operator = memberMapper.findByConversationAndUser(conversation.getId(), operatorUserId);
        if (operator == null || !"OWNER".equals(operator.getMemberRole())) {
            return;
        }
        persistSystemMessage(conversation.getId(), "群主已修改行程信息，点击“行程信息”查看最新安排");
        memberMapper.incrementUnread(conversation.getId(), operatorUserId, LocalDateTime.now());
    }

    /** 发送消息并刷新会话最后一条消息摘要。 */
    @Override
    @Transactional
    public MessageResponse sendMessage(Long conversationId, SendMessageRequest request) {
        Long userId = currentUserContext.requireUserId();
        // 权限校验必须在解析和持久化消息之前完成，避免越权用户制造消息或风险记录。
        requireActiveMember(conversationId, userId);
        ChatConversation conversation = conversationMapper.findById(conversationId);
        if (conversation == null || !List.of("ACTIVE", "HISTORY").contains(conversation.getConversationStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在或已归档");
        }
        conversation = ensureTencentProvider(conversation);
        if ("PRIVATE".equals(conversation.getBizType())) {
            // 陌生人私聊存在三条文字限额及媒体发送限制，群聊不适用该规则。
            validatePrivateMessage(conversation, userId, request);
        }
        boolean imageMessage = "IMAGE".equalsIgnoreCase(request.messageType());
        if (!imageMessage && !StringUtils.hasText(request.content())) {
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
        // 业务扩展字段与文本统一存为 JSON，便于图片、行程卡片等消息复用同一张表。
        messagePayload.put("content", imageMessage ? "" : request.content());
        if (request.payload() != null) messagePayload.putAll(request.payload());
        if (imageMessage) {
            // 图片消息只保留访问所需元数据，不向聊天记录暴露手机原始文件名。
            messagePayload.remove("fileName");
            messagePayload.remove("originalFileName");
            messagePayload.remove("name");
        }
        message.setMessagePayloadJson(toJson(messagePayload));
        RiskDecision risk = imageMessage ? null : detectRisk(request.content());
        // BLOCKED 不投递；RISK_REVIEW 仍正常送达但会生成待审核事实记录。
        message.setMessageStatus(risk == null ? "NORMAL" : (risk.blocked() ? "BLOCKED" : "RISK_REVIEW"));
        message.setProviderMessageKey("local-msg-" + message.getId());
        message.setSentAt(now);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        if (risk == null || !risk.blocked()) {
            // 腾讯 IM 当前封装的是文本通道；图片消息发送占位摘要，真实图片仍由 MinIO payload 展示。
            String providerContent = imageMessage ? "[图片]" : request.content();
            String senderImUserId = TencentImServiceImpl.toImUserId(userId);
            if ("PRIVATE".equals(conversation.getBizType())) {
                ChatConversationMember peer = memberMapper.findOtherActive(conversationId, userId);
                if (peer == null) {
                    throw new BusinessException(ResultCode.NOT_FOUND, "私聊对象不存在");
                }
                message.setProviderMessageKey(tencentImService.sendC2CText(
                        TencentImServiceImpl.toImUserId(peer.getUserId()),
                        senderImUserId,
                        providerContent
                ));
            } else {
                message.setProviderMessageKey(tencentImService.sendGroupText(
                        requireGroupProviderKey(conversation),
                        senderImUserId,
                        providerContent
                ));
            }
        }
        messageMapper.insert(message);
        // 无论是否拦截，消息事实都需要落库，确保风控结果可审计且客户端能看到发送结果。
        if (risk != null) {
            persistRisk(message.getId(), risk, now);
        }
        if (risk != null && risk.blocked()) {
            // 被拦截消息不能更新会话摘要和成员未读数，也不会进入腾讯云通道。
            return toMessageResponse(message);
        }
        conversationMapper.updateLastMessage(conversationId, message.getId(), imageMessage ? "[图片]" : preview(request.content()), now);
        // 给除发送者之外的有效成员增加未读数。
        memberMapper.incrementUnread(conversationId, userId, now);
        return toMessageResponse(message);
    }

    /** 添加会话成员，并同步到腾讯云 IM 群组。 */
    @Override
    @Transactional
    public ConversationMemberResponse addMember(Long conversationId, Long userId) {
        // 任何当前有效群成员都可以邀请其他用户；未入群用户不能枚举 conversationId 拉人。
        Long operatorUserId = currentUserContext.requireUserId();
        requireActiveMember(conversationId, operatorUserId);
        ChatConversation conversation = conversationMapper.findById(conversationId);
        if (conversation == null || !"ACTIVE".equals(conversation.getConversationStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "群聊不存在或已结束");
        }
        conversation = ensureTencentProvider(conversation);
        if ("PRIVATE".equals(conversation.getBizType())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "私聊不能添加群成员");
        }
        if (userId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "被邀请用户不能为空");
        }

        ChatConversationMember existed = memberMapper.findByConversationAndUser(conversationId, userId);
        if (existed != null && "ACTIVE".equals(existed.getMemberStatus())) {
            // 重复邀请按幂等成功处理，避免再次调用腾讯 IM 触发“成员已存在”错误。
            return toMemberResponse(existed);
        }
        // 在调用腾讯 IM 前确认业务用户真实存在，防止云端产生无法映射的账号。
        userService.getChatMemberProfile(userId);
        tencentImService.addGroupMember(requireGroupProviderKey(conversation),
                TencentImServiceImpl.toImUserId(userId));
        ChatConversationMember member = addMemberInternal(conversationId, userId, "MEMBER");
        persistSystemMessage(conversationId, publicName(operatorUserId) + " 邀请 " + publicName(userId) + " 加入群聊");
        return toMemberResponse(member);
    }

    @Override
    @Transactional
    public void addApprovedTripMember(Long tripId, Long userId) {
        // 行程审批回调可能早于群聊创建，必要时先根据行程成员补建整个会话。
        ChatConversation conversation = conversationMapper.findByBiz("TRIP", tripId);
        if (conversation == null) {
            TripResponse trip = tripService.getTrip(tripId);
            List<Long> members = tripService.getMembers(tripId).stream()
                    .filter(member -> "OWNER".equals(member.joinStatus()) || "APPROVED".equals(member.joinStatus()))
                    .map(member -> Long.valueOf(member.userId()))
                    .distinct()
                    .toList();
            prepareTripConversation(tripId, trip.title(), Long.valueOf(trip.userId()), members);
            conversation = conversationMapper.findByBiz("TRIP", tripId);
        }
        if (conversation == null || !"ACTIVE".equals(conversation.getConversationStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "行程群聊尚未就绪");
        }
        conversation = ensureTencentProvider(conversation);
        ChatConversationMember before = memberMapper.findByConversationAndUser(conversation.getId(), userId);
        if (before != null && "ACTIVE".equals(before.getMemberStatus())) {
            // 审批接口重试时直接返回，防止重复加云群和重复发送“已加入”系统消息。
            return;
        }
        tencentImService.addGroupMember(requireGroupProviderKey(conversation),
                TencentImServiceImpl.toImUserId(userId));
        addMemberInternal(conversation.getId(), userId, "MEMBER");
        persistSystemMessage(conversation.getId(), publicName(userId) + " 已通过行程申请并加入群聊");
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
        if (conversation == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        conversation = ensureTencentProvider(conversation);
        if ("TRIP".equals(conversation.getBizType())) {
            // 先释放关联业务的成员名额，再把聊天成员标记为退出，保持三个模块状态一致。
            teamService.exitTrip(conversation.getBizId());
        } else if ("TEAM".equals(conversation.getBizType())) {
            teamService.exit(conversation.getBizId());
        }
        String exitMessage = publicName(userId) + " 已退出群聊及关联行程";
        persistSystemMessage(conversationId, exitMessage);
        if (!"PRIVATE".equals(conversation.getBizType())) {
            String imUserId = TencentImServiceImpl.toImUserId(userId);
            try {
                // persistSystemMessage 已把退出通知作为自定义消息同步到腾讯 IM；这里仅移除成员，避免重复消息。
                tencentImService.removeGroupMember(requireGroupProviderKey(conversation), imUserId);
            } catch (RuntimeException ex) {
                log.warn("从腾讯 IM 群移除成员失败，conversationId={}, userId={}",
                        conversationId, userId, ex);
            }
        }
        int changed = memberMapper.exit(conversationId, userId, LocalDateTime.now());
        if (changed == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权退出该会话");
        }
    }

    /** 返回会话内仍处于 ACTIVE 状态的成员及其公开资料。 */
    @Override
    public List<ConversationMemberResponse> getMembers(Long conversationId) {
        Long userId = currentUserContext.requireUserId();
        requireActiveMember(conversationId, userId);
        return memberMapper.findActiveByConversationId(conversationId).stream()
                .map(this::toMemberResponse).toList();
    }

    /** 查询当前用户在会话中的免打扰和置顶设置。 */
    @Override
    public ConversationSettingResponse getSettings(Long conversationId) {
        ChatConversationMember member = requireActiveMember(conversationId, currentUserContext.requireUserId());
        return toSettingResponse(member);
    }

    /** 更新当前用户独有的会话设置，不影响其他成员。 */
    @Override
    public ConversationSettingResponse updateSettings(Long conversationId, boolean muted, boolean pinned) {
        Long userId = currentUserContext.requireUserId();
        requireActiveMember(conversationId, userId);
        memberMapper.updateSettings(conversationId, userId, muted, pinned, LocalDateTime.now());
        return new ConversationSettingResponse(String.valueOf(conversationId), muted, pinned);
    }

    /** 提交入群申请；有效成员和已有待审申请不能重复提交。 */
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
        // 申请记录是审核事实，不能直接激活成员；只有 APPROVED 审核路径才真正加群。
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

    /** 查询当前用户作为群主可审核的入群申请队列。 */
    @Override
    public List<JoinApplicationResponse> getJoinApplications(String status) {
        String normalized = StringUtils.hasText(status) ? status.trim().toUpperCase() : "PENDING";
        if (!List.of("PENDING", "APPROVED", "REJECTED").contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "申请状态不合法");
        }
        return joinApplicationMapper.findOwnerQueue(currentUserContext.requireUserId(), normalized)
                .stream().map(this::toJoinApplicationResponse).toList();
    }

    /** 原子审核入群申请；仅首次状态变更成功时执行后续加群动作。 */
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
        // Mapper 更新条件包含 PENDING，可避免两个审核请求同时通过同一申请。
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

    /** 校验私聊目标存在且不是当前用户本人。 */
    private void validatePrivateTarget(Long currentUserId, Long targetUserId) {
        if (targetUserId == null || currentUserId.equals(targetUserId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能与自己发起私聊");
        }
        userService.getChatMemberProfile(targetUserId);
    }

    /** 生成与发起方向无关的私聊唯一业务键。 */
    private String privatePairKey(Long first, Long second) {
        long min = Math.min(first, second);
        long max = Math.max(first, second);
        return "private:" + min + ":" + max;
    }

    /** 恢复旧私聊时优先沿用成员原角色，缺失时才使用默认角色。 */
    private String existingPrivateRole(Long conversationId, Long userId, String fallback) {
        ChatConversationMember member = memberMapper.findByConversationAndUser(conversationId, userId);
        return member == null || !StringUtils.hasText(member.getMemberRole()) ? fallback : member.getMemberRole();
    }

    /**
     * 综合关注关系、共同有效行程和对方是否回复，计算私聊是否解锁。
     * 未解锁时只有会话发起者受“三条文字”限制，接收方可以回复以解锁双方能力。
     */
    private PrivateChatPermissionResponse privatePermission(Long currentUserId, Long targetUserId,
                                                            ChatConversation existing) {
        var follow = userService.getFollowStatus(targetUserId);
        boolean sharedTrip = memberMapper.countSharedTrip(currentUserId, targetUserId) > 0;
        boolean replied = false;
        boolean currentIsInitiator = true;
        int sentByInitiator = 0;
        if (existing != null) {
            // OWNER 表示首次发起者；角色用于确定限额归属，不代表私聊中的管理权限。
            ChatConversationMember mine = memberMapper.findByConversationAndUser(existing.getId(), currentUserId);
            ChatConversationMember peer = memberMapper.findByConversationAndUser(existing.getId(), targetUserId);
            currentIsInitiator = mine == null || "OWNER".equals(mine.getMemberRole());
            Long initiatorId = currentIsInitiator ? currentUserId : targetUserId;
            Long recipientId = currentIsInitiator ? targetUserId : currentUserId;
            sentByInitiator = messageMapper.countSentByUser(existing.getId(), initiatorId);
            replied = messageMapper.countSentByUser(existing.getId(), recipientId) > 0;
            if (peer == null) {
                replied = false;
            }
        }
        boolean mutual = Boolean.TRUE.equals(follow.mutual());
        // 任一可信关系成立即解锁媒体消息；单向关注只允许创建会话，不直接解锁。
        boolean unlocked = mutual || sharedTrip || replied;
        int remaining = unlocked || !currentIsInitiator ? 3 : Math.max(0, 3 - sentByInitiator);
        String relationType;
        if (mutual) relationType = "MUTUAL";
        else if (sharedTrip) relationType = "SAME_TRIP";
        else if (replied) relationType = "REPLIED";
        else if (Boolean.TRUE.equals(follow.following())) relationType = "FOLLOWING";
        else if (Boolean.TRUE.equals(follow.followedByTarget())) relationType = "FOLLOWER";
        else relationType = "STRANGER";
        boolean canStart = existing != null || mutual || sharedTrip || Boolean.TRUE.equals(follow.following());
        String reason = canStart ? "" : "关注对方后即可发起私聊";
        return new PrivateChatPermissionResponse(String.valueOf(targetUserId), canStart,
                existing == null ? null : String.valueOf(existing.getId()), relationType,
                remaining, unlocked, unlocked, reason);
    }

    /** 在发送前执行私聊消息类型和三条文字额度校验。 */
    private void validatePrivateMessage(ChatConversation conversation, Long userId, SendMessageRequest request) {
        ChatConversationMember peer = memberMapper.findOtherActive(conversation.getId(), userId);
        if (peer == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "私聊对象不存在");
        }
        PrivateChatPermissionResponse permission = privatePermission(userId, peer.getUserId(), conversation);
        if (permission.unlocked()) {
            return;
        }
        if (!"TEXT".equalsIgnoreCase(request.messageType())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "对方回复或互相关注后才可发送图片和行程卡片");
        }
        ChatConversationMember mine = memberMapper.findByConversationAndUser(conversation.getId(), userId);
        if (mine != null && "OWNER".equals(mine.getMemberRole()) && permission.remainingTextMessages() <= 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "对方回复前最多发送 3 条文字消息");
        }
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
        // 兼容表继续保存系统消息，便于后台审计和旧客户端读取。
        conversationMapper.updateLastMessage(conversationId, message.getId(), content, now);

        ChatConversation conversation = conversationMapper.findById(conversationId);
        if (conversation != null && !"PRIVATE".equals(conversation.getBizType())) {
            String groupId = requireGroupProviderKey(conversation);
            try {
                // App 已改为读取腾讯 IM 历史，因此系统通知也必须进入腾讯 IM 消息流。
                String payload = toJson(Map.of(
                        "version", 1,
                        // 发送后回调据此识别后端已经保存的系统消息，避免重复落库。
                        "localMessageId", String.valueOf(message.getId()),
                        "type", "SYSTEM",
                        "content", content
                ));
                tencentImService.sendGroupCustom(groupId, null,
                        payload, content, "SYSTEM");
            } catch (RuntimeException ex) {
                // 本地审计已经成功；云端同步失败先记录，避免生命周期事务整体回滚。
                log.warn("同步系统消息到腾讯 IM 失败，conversationId={}", conversationId, ex);
            }
        }
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
        // 调用方已校验普通文本非空；统一转小写便于后续扩展英文关键词规则。
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

    /** 判断文本是否命中任意一个本地风险关键词。 */
    private boolean containsAny(String content, String... keywords) {
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /** 一次风险识别结果；blocked 决定是否阻断发送，其余字段用于后台审核。 */
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
            // 退出成员复用原数据行并重置个人会话状态，避免唯一键冲突和历史成员重复。
            memberMapper.reactivate(conversationId, userId, role, now);
            existed.setMemberRole(role);
            existed.setMemberStatus("ACTIVE");
            existed.setUnreadCount(0);
            existed.setClearedBeforeMessageId(null);
            existed.setJoinedAt(now);
            return existed;
        }
        ChatConversationMember member = new ChatConversationMember();
        // 新成员从零未读、非置顶、非免打扰开始，加入时间与审计时间使用同一时刻。
        member.setId(SnowflakeIdGenerator.nextId());
        member.setConversationId(conversationId);
        member.setUserId(userId);
        member.setMemberRole(role);
        member.setMemberStatus("ACTIVE");
        member.setUnreadCount(0);
        member.setMutedFlag(false);
        member.setPinnedFlag(false);
        member.setClearedBeforeMessageId(null);
        member.setJoinedAt(now);
        member.setCreatedAt(now);
        member.setUpdatedAt(now);
        memberMapper.insert(member);
        return member;
    }

    /**
     * 幂等迁移数据库中遗留的非腾讯 IM 会话记录。
     *
     * <p>私聊无需创建云端会话；群聊按稳定 GroupId 创建或复用，并同步全部有效成员。</p>
     */
    private ChatConversation ensureTencentProvider(ChatConversation conversation) {
        if ("TENCENT_IM".equals(conversation.getProviderType())) {
            return conversation;
        }
        String providerKey = conversation.getProviderConversationKey();
        if ("PRIVATE".equals(conversation.getBizType())) {
            // 私聊通过 C2C 投递，不存在需要补建的云端群，只需升级 provider 标记。
            conversationMapper.updateProvider(conversation.getId(), "TENCENT_IM", providerKey, LocalDateTime.now());
            conversation.setProviderType("TENCENT_IM");
            return conversation;
        }
        if (!StringUtils.hasText(providerKey)) {
            providerKey = "TRIP".equals(conversation.getBizType())
                    ? "trip_" + conversation.getBizId()
                    : groupId(conversation.getBizId());
        }
        List<ChatConversationMember> members = memberMapper.findActiveByConversationId(conversation.getId());
        // 腾讯云建群必须指定 owner；若历史数据缺失 OWNER，则退化选择首个有效成员。
        ChatConversationMember owner = members.stream()
                .filter(member -> "OWNER".equals(member.getMemberRole()))
                .findFirst()
                .orElseGet(() -> members.stream().findFirst().orElse(null));
        if (owner == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "会话没有可迁移的有效成员");
        }
        String ownerImUserId = TencentImServiceImpl.toImUserId(owner.getUserId());
        tencentImService.createGroup(providerKey, ownerImUserId, conversation.getConversationName());
        // owner 已随 createGroup 入群，其余成员逐个补入，避免重复添加群主。
        for (ChatConversationMember member : members) {
            if (!member.getUserId().equals(owner.getUserId())) {
                tencentImService.addGroupMember(providerKey,
                        TencentImServiceImpl.toImUserId(member.getUserId()));
            }
        }
        conversationMapper.updateProvider(conversation.getId(), "TENCENT_IM", providerKey, LocalDateTime.now());
        conversation.setProviderType("TENCENT_IM");
        conversation.setProviderConversationKey(providerKey);
        return conversation;
    }


    /**
     * 返回真实腾讯 IM GroupId；缺少绑定时直接失败，禁止退回本地群操作。
     */
    private String requireGroupProviderKey(ChatConversation conversation) {
        if (conversation == null || "PRIVATE".equals(conversation.getBizType())
                || !StringUtils.hasText(conversation.getProviderConversationKey())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "群聊缺少腾讯 IM GroupId");
        }
        return conversation.getProviderConversationKey();
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
        String name = conversation.getConversationName();
        String avatarImageKey = "";
        String avatarAccessUrl = "";
        String peerUserId = null;
        String relationType = "GROUP";
        int remainingTextMessages = 0;
        boolean canSendMedia = true;
        if ("PRIVATE".equals(conversation.getBizType()) && member != null) {
            // 私聊列表展示对方资料而不是固定“私聊”名称，并附带实时权限状态。
            ChatConversationMember peer = memberMapper.findOtherActive(conversation.getId(), member.getUserId());
            if (peer != null) {
                PublicProfileVO profile = safePublicProfile(peer.getUserId());
                name = profile.nickname();
                avatarImageKey = profile.avatarImageKey();
                avatarAccessUrl = avatarUrl(profile.avatarImageKey());
                peerUserId = String.valueOf(peer.getUserId());
                PrivateChatPermissionResponse permission = privatePermission(member.getUserId(), peer.getUserId(), conversation);
                relationType = permission.relationType();
                remainingTextMessages = permission.remainingTextMessages();
                canSendMedia = permission.canSendMedia();
            }
        }
        boolean clearedLastMessage = member != null && member.getClearedBeforeMessageId() != null
                && conversation.getLastMessageId() != null
                && conversation.getLastMessageId() <= member.getClearedBeforeMessageId();
        // MOCK 已移除，客户端始终按腾讯 IM 会话处理。
        String effectiveProviderType = "TENCENT_IM";
        String providerKey = conversation.getProviderConversationKey();
        String imConversationId;
        if ("PRIVATE".equals(conversation.getBizType()) && peerUserId != null) {
            // 腾讯 IM 单聊会话 ID 固定为 c2c_ + 对方 IM 用户 ID。
            imConversationId = "c2c_" + TencentImServiceImpl.toImUserId(Long.valueOf(peerUserId));
        } else if (StringUtils.hasText(providerKey)) {
            // 腾讯 IM 群会话 ID 固定为 group_ + 原始 GroupId。
            imConversationId = "group_" + providerKey;
        } else {
            imConversationId = "";
        }
        return new ConversationResponse(
                String.valueOf(conversation.getId()),
                conversation.getBizType(),
                String.valueOf(conversation.getBizId()),
                name,
                conversation.getConversationStatus(),
                effectiveProviderType,
                providerKey,
                imConversationId,
                clearedLastMessage ? "" : conversation.getLastMessagePreview(),
                clearedLastMessage ? null : format(conversation.getLastMessageAt()),
                member != null && Boolean.TRUE.equals(member.getPinnedFlag()),
                member != null && Boolean.TRUE.equals(member.getMutedFlag()),
                member == null || member.getUnreadCount() == null ? 0 : member.getUnreadCount(),
                avatarImageKey,
                avatarAccessUrl,
                peerUserId,
                relationType,
                remainingTextMessages,
                canSendMedia
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
                avatarUrl(profile.avatarImageKey()),
                profile.totalTripCount(),
                profile.totalDistanceMeters(),
                profile.completedWaypointCount(),
                Boolean.TRUE.equals(member.getMutedFlag()),
                Boolean.TRUE.equals(member.getPinnedFlag()),
                format(member.getJoinedAt())
        );
    }

    /** 将成员的个人会话设置转换为接口响应。 */
    private ConversationSettingResponse toSettingResponse(ChatConversationMember member) {
        return new ConversationSettingResponse(String.valueOf(member.getConversationId()),
                Boolean.TRUE.equals(member.getMutedFlag()), Boolean.TRUE.equals(member.getPinnedFlag()));
    }

    /** 组装入群申请，并补充会话名称、申请人资料和双方关注关系。 */
    private JoinApplicationResponse toJoinApplicationResponse(ChatJoinApplication row) {
        PublicProfileVO profile = safePublicProfile(row.getApplicantUserId());
        ChatConversation conversation = conversationMapper.findById(row.getConversationId());
        var relation = userService.getFollowStatus(row.getApplicantUserId());
        return new JoinApplicationResponse(String.valueOf(row.getId()), String.valueOf(row.getConversationId()),
                conversation == null ? "车队群聊" : conversation.getConversationName(),
                String.valueOf(row.getApplicantUserId()), profile.nickname(), profile.avatarImageKey(),
                row.getApplicationMessage(), row.getApplicationStatus(), format(row.getCreatedAt()),
                Boolean.TRUE.equals(relation.following()), Boolean.TRUE.equals(relation.followedByTarget()),
                Boolean.TRUE.equals(relation.mutual()));
    }

    /** 获取系统消息使用的公开昵称，资料异常或昵称为空时使用安全兜底。 */
    private String publicName(Long userId) {
        String name = safePublicProfile(userId).nickname();
        return StringUtils.hasText(name) ? name : "新成员";
    }

    /**
     * 安全读取聊天公开资料。
     * 用户模块短暂失败不应导致整页会话或消息不可用，因此这里只降级展示默认资料。
     */
    private PublicProfileVO safePublicProfile(Long userId) {
        try {
            PublicProfileVO profile = userService.getChatMemberProfile(userId);
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
                sender == null ? null : avatarUrl(sender.avatarImageKey()),
                readPayload(message.getMessagePayloadJson()),
                message.getMessageStatus(),
                format(message.getSentAt())
        );
    }

    /** 将头像对象 Key 转换为短期有效的访问地址；头像缺失或签名失败时返回空串。 */
    private String avatarUrl(String objectKey) {
        if (!StringUtils.hasText(objectKey) || objectKey.startsWith("data:")) {
            return "";
        }
        try {
            return storageService.presignDownload(null, null, objectKey).downloadUrl();
        } catch (RuntimeException ignored) {
            return "";
        }
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

    /** 解析完整消息 payload；历史脏数据解析失败时返回空对象以保证消息列表可展示。 */
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
        // “清空聊天”只隐藏清空前的摘要，不删除消息；新消息 ID 越过游标后摘要自然恢复。
