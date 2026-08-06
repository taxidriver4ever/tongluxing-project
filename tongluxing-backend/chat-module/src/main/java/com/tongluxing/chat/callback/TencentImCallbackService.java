package com.tongluxing.chat.callback;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.chat.config.TencentImProperties;
import com.tongluxing.chat.entity.ChatConversation;
import com.tongluxing.chat.entity.ChatMessage;
import com.tongluxing.chat.entity.ChatConversationMember;
import com.tongluxing.chat.entity.MessageRisk;
import com.tongluxing.chat.mapper.ChatConversationMapper;
import com.tongluxing.chat.mapper.ChatConversationMemberMapper;
import com.tongluxing.chat.mapper.ChatMessageMapper;
import com.tongluxing.chat.mapper.MessageRiskMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.storage.service.StorageService;
import com.tongluxing.storage.vo.FileMetadataResponse;
import com.tongluxing.user.mapper.UserFollowMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 腾讯 IM 回调处理服务。
 *
 * <p>客户端直接通过腾讯 IM SDK 收发消息后，腾讯 IM 会把消息发送后、成员退出和群解散事件
 * 推送到该服务。这里保存的是举报、风控和审计副本，不再给 App 充当普通聊天历史接口。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TencentImCallbackService {

    private static final long CALLBACK_TIME_WINDOW_SECONDS = 60L;

    private final TencentImProperties properties;
    private final ObjectMapper objectMapper;
    private final ChatConversationMapper conversationMapper;
    private final ChatConversationMemberMapper memberMapper;
    private final ChatMessageMapper messageMapper;
    private final MessageRiskMapper riskMapper;
    private final UserFollowMapper userFollowMapper;
    private final StorageService storageService;

    /** 校验腾讯 IM 回调 URL 中的 SDKAppID、时间戳和 SHA-256 签名。 */
    public void verify(Long sdkAppId, Long requestTime, String sign) {
        if (sdkAppId == null || !sdkAppId.equals(properties.getSdkAppId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "腾讯 IM 回调 SDKAppID 不匹配");
        }
        if (requestTime == null || Math.abs(Instant.now().getEpochSecond() - requestTime) > CALLBACK_TIME_WINDOW_SECONDS) {
            throw new BusinessException(ResultCode.FORBIDDEN, "腾讯 IM 回调时间戳已过期");
        }
        if (!StringUtils.hasText(properties.getCallbackToken())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "腾讯 IM 回调 Token 未配置");
        }

        // 腾讯 IM 回调签名规则：SHA256(callbackToken + requestTime)。
        String expected = sha256(properties.getCallbackToken() + requestTime);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                String.valueOf(sign).getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException(ResultCode.FORBIDDEN, "腾讯 IM 回调签名校验失败");
        }
    }

    /**
     * 按 CallbackCommand 分发回调并返回腾讯 IM 规定的响应结构。
     *
     * <p>发送前回调必须同步返回允许或拒绝；发送后及成员事件只做幂等同步并返回允许。</p>
     */
    @Transactional
    public Map<String, Object> handle(String command, Map<String, Object> body) {
        if (!StringUtils.hasText(command)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "腾讯 IM 回调命令不能为空");
        }
        return switch (command) {
            case "Group.CallbackBeforeSendMsg" -> beforeGroupMessage(body);
            case "C2C.CallbackBeforeSendMsg" -> beforeC2CMessage(body);
            case "Group.CallbackAfterSendMsg" -> {
                saveGroupMessage(command, body);
                yield allow();
            }
            case "C2C.CallbackAfterSendMsg" -> {
                saveC2CMessage(command, body);
                yield allow();
            }
            case "Group.CallbackAfterNewMemberJoin" -> {
                syncMemberJoin(body);
                yield allow();
            }
            case "Group.CallbackAfterMemberExit" -> {
                syncMemberExit(body);
                yield allow();
            }
            case "Group.CallbackAfterGroupDestroyed" -> {
                syncGroupDestroyed(body);
                yield allow();
            }
            default -> {
                log.debug("忽略暂未处理的腾讯 IM 回调：{}", command);
                yield allow();
            }
        };
    }

    /**
     * 群消息发送前校验。
     *
     * <p>只执行成员状态、群状态、基础消息格式和高风险关键词等快速检查，
     * 避免腾讯 IM 等待回调时被复杂网络调用或长事务阻塞。</p>
     */
    private Map<String, Object> beforeGroupMessage(Map<String, Object> body) {
        ChatConversation conversation = conversationMapper
                .findByProviderConversationKey(string(body.get("GroupId")));
        if (conversation == null || !"ACTIVE".equals(conversation.getConversationStatus())) {
            return reject("群聊已结束或不存在");
        }
        String fromAccount = string(body.get("From_Account"));
        Long senderUserId = businessUserId(fromAccount);

        // 后端通过管理员账号发送的系统卡片属于可信业务消息，不要求管理员本身加入群聊。
        // 普通用户消息仍必须对应本地 ACTIVE 成员，防止仅知道 GroupId 就绕过业务权限发言。
        boolean backendSystemMessage = StringUtils.hasText(properties.getAdminUserId())
                && properties.getAdminUserId().equals(fromAccount);
        if (!backendSystemMessage) {
            ChatConversationMember member = senderUserId == null ? null
                    : memberMapper.findByConversationAndUser(conversation.getId(), senderUserId);
            if (member == null || !"ACTIVE".equals(member.getMemberStatus())) {
                return reject("你已不在该群聊中");
            }
        }
        return validateMessageBody(body.get("MsgBody"), conversation.getId(), senderUserId,
                backendSystemMessage);
    }

    /**
     * 单聊消息发送前校验。
     *
     * <p>除基础风控外，还在服务端执行陌生人三条文字限制。互相关注、存在共同
     * 行程或对方已经回复后，媒体消息和后续文字消息自动解锁。</p>
     */
    private Map<String, Object> beforeC2CMessage(Map<String, Object> body) {
        Long fromUserId = businessUserId(string(body.get("From_Account")));
        Long toUserId = businessUserId(string(body.get("To_Account")));
        if (fromUserId == null || toUserId == null) return reject("用户账号格式错误");

        String pairKey = "private:" + Math.min(fromUserId, toUserId) + ':' + Math.max(fromUserId, toUserId);
        ChatConversation conversation = conversationMapper.findByProviderKey("PRIVATE", pairKey);
        if (conversation == null || !"ACTIVE".equals(conversation.getConversationStatus())) {
            return reject("私聊会话不存在");
        }
        ChatConversationMember mine = memberMapper.findByConversationAndUser(conversation.getId(), fromUserId);
        ChatConversationMember peer = memberMapper.findByConversationAndUser(conversation.getId(), toUserId);
        if (mine == null || peer == null || !"ACTIVE".equals(mine.getMemberStatus())
                || !"ACTIVE".equals(peer.getMemberStatus())) {
            return reject("私聊关系已失效");
        }

        Map<String, Object> basicDecision = validateMessageBody(
                body.get("MsgBody"), conversation.getId(), fromUserId, false);
        if (!Integer.valueOf(0).equals(basicDecision.get("ErrorCode"))) return basicDecision;

        Map<String, Object> normalized = normalizeBody(body.get("MsgBody"));
        String messageType = string(normalized.get("messageType"));
        boolean mutual = userFollowMapper.exists(fromUserId, toUserId) > 0
                && userFollowMapper.exists(toUserId, fromUserId) > 0;
        boolean sharedTrip = memberMapper.countSharedTrip(fromUserId, toUserId) > 0;
        boolean replied = messageMapper.countSentByUser(conversation.getId(), toUserId) > 0;
        boolean unlocked = mutual || sharedTrip || replied;

        if (!unlocked && !"TEXT".equalsIgnoreCase(messageType)) {
            return reject("对方回复、互相关注或共同出行后才可发送图片和位置");
        }
        if (!unlocked && "OWNER".equals(mine.getMemberRole())
                && messageMapper.countSentByUser(conversation.getId(), fromUserId) >= 3) {
            return reject("对方回复前最多发送 3 条文字消息");
        }
        return allow();
    }

    /**
     * 校验文本高风险关键词以及自定义媒体消息的业务归属。
     *
     * <p>媒体消息不能只检查 fileId 是否存在，还必须确认文件已经上传完成、绑定当前会话，
     * 并且确实由本次消息发送者上传，防止伪造其他用户的 fileId。</p>
     */
    private Map<String, Object> validateMessageBody(
            Object rawBody, Long conversationId, Long senderUserId, boolean backendSystemMessage) {
        Map<String, Object> normalized = normalizeBody(rawBody);
        String type = string(normalized.get("messageType")).toUpperCase();
        String content = string(normalized.get("content"));
        RiskDecision risk = detectRisk(content);
        if (risk != null && risk.blocked()) return reject("消息包含高风险内容，已拒绝发送");

        if ("CHAT_IMAGE".equals(type) || "CHAT_FILE".equals(type)) {
            // 管理员系统消息不允许伪装成媒体消息；业务系统卡片应使用 SYSTEM/*_CARD 类型。
            if (backendSystemMessage || senderUserId == null) {
                return reject("系统账号不能发送聊天媒体");
            }
            String rawFileId = StringUtils.hasText(string(normalized.get("fileId")))
                    ? string(normalized.get("fileId")) : string(normalized.get("mediaId"));
            if (!StringUtils.hasText(rawFileId)) return reject("图片或文件消息缺少 mediaId");

            final Long fileId;
            try {
                fileId = Long.valueOf(rawFileId);
            } catch (NumberFormatException exception) {
                return reject("图片或文件 mediaId 格式错误");
            }
            try {
                FileMetadataResponse file = storageService.getMetadata(fileId);
                String expectedBizType = "CHAT_IMAGE".equals(type) ? "CHAT_IMAGE" : "CHAT_FILE";
                if (!expectedBizType.equals(file.bizType())
                        || !String.valueOf(conversationId).equals(file.bizId())
                        || !senderUserId.equals(file.userId())
                        || !"CONFIRMED".equals(file.uploadStatus())) {
                    return reject("图片或文件不属于当前会话或发送者");
                }
            } catch (BusinessException exception) {
                return reject("图片或文件不存在或尚未上传完成");
            }
        }
        return allow();
    }

    private Map<String, Object> allow() {
        return Map.of("ActionStatus", "OK", "ErrorCode", 0, "ErrorInfo", "");
    }

    private Map<String, Object> reject(String message) {
        // ErrorCode=1 表示拒绝下发；腾讯 IM 会把发送失败结果返回客户端。
        return Map.of("ActionStatus", "OK", "ErrorCode", 1, "ErrorInfo", message);
    }

    /** 保存群聊消息的审计副本。 */
    private void saveGroupMessage(String command, Map<String, Object> body) {
        String groupId = string(body.get("GroupId"));
        ChatConversation conversation = conversationMapper.findByProviderConversationKey(groupId);
        if (conversation == null) {
            log.warn("腾讯 IM 群消息未找到本地绑定，groupId={}", groupId);
            return;
        }
        saveMessage(command, conversation, body, string(body.get("From_Account")));
    }

    /** 保存单聊消息的审计副本。 */
    private void saveC2CMessage(String command, Map<String, Object> body) {
        Long fromUserId = businessUserId(string(body.get("From_Account")));
        Long toUserId = businessUserId(string(body.get("To_Account")));
        if (fromUserId == null || toUserId == null) {
            log.warn("腾讯 IM 单聊回调账号格式不符合 u_<userId>：from={}, to={}",
                    body.get("From_Account"), body.get("To_Account"));
            return;
        }
        String pairKey = "private:" + Math.min(fromUserId, toUserId) + ":" + Math.max(fromUserId, toUserId);
        ChatConversation conversation = conversationMapper.findByProviderKey("PRIVATE", pairKey);
        if (conversation == null) {
            log.warn("腾讯 IM 单聊消息未找到本地业务会话，pairKey={}", pairKey);
            return;
        }
        saveMessage(command, conversation, body, string(body.get("From_Account")));
    }

    /** 解析腾讯消息体并进行幂等落库。 */
    private void saveMessage(String command, ChatConversation conversation,
                             Map<String, Object> body, String fromAccount) {
        Map<String, Object> normalized = normalizeBody(body.get("MsgBody"));

        // 兼容旧客户端：旧接口由后端先调用腾讯 IM，再用 tencent-<MsgRandom> 落库。
        // 回调若再次保存同一条消息，会导致旧小程序历史列表出现重复记录。
        String legacyProviderKey = legacyProviderMessageKey(body);
        if (legacyProviderKey != null
                && messageMapper.countByProviderMessageKey(legacyProviderKey) > 0) {
            return;
        }

        // 后端业务卡片在调用腾讯 IM 前已经写入本地，并把本地消息 ID 放进自定义协议。
        // 收到发送后回调时只确认已有事实，不再创建第二条审计记录。
        Long localMessageId = longValue(normalized.get("localMessageId"));
        if (localMessageId != null
                && messageMapper.countByIdAndConversation(localMessageId, conversation.getId()) > 0) {
            return;
        }

        String providerKey = providerMessageKey(command, body);
        if (messageMapper.countByProviderMessageKey(providerKey) > 0) {
            return;
        }

        String messageType = string(normalized.remove("messageType"));
        String preview = preview(messageType, normalized);
        Object timestamp = body.get("MsgTime") != null ? body.get("MsgTime") : body.get("MsgTimeStamp");
        LocalDateTime sentAt = callbackTime(timestamp);

        RiskDecision risk = detectRisk(string(normalized.get("content")));
        ChatMessage message = new ChatMessage();
        message.setId(SnowflakeIdGenerator.nextId());
        message.setConversationId(conversation.getId());
        message.setSenderUserId(businessUserId(fromAccount));
        message.setMessageType(messageType);
        message.setMessagePayloadJson(write(normalized));
        // 高风险理论上已被发送前回调拦截；发送后仍命中时进入审核，便于发现回调配置遗漏。
        message.setMessageStatus(risk == null ? "NORMAL" : "RISK_REVIEW");
        message.setProviderMessageKey(providerKey);
        message.setSentAt(sentAt);
        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        messageMapper.insert(message);
        if (risk != null) persistRisk(message.getId(), risk);
        conversationMapper.updateLastMessage(conversation.getId(), message.getId(), preview, sentAt);
    }

    /** 新成员通过腾讯 IM 加入后，幂等补齐本地业务成员记录。 */
    private void syncMemberJoin(Map<String, Object> body) {
        ChatConversation conversation = conversationMapper.findByProviderConversationKey(string(body.get("GroupId")));
        if (conversation == null) return;
        Object joined = body.get("NewMemberList");
        if (!(joined instanceof List<?> list)) return;
        for (Object row : list) {
            String account = row instanceof Map<?, ?> map ? string(map.get("Member_Account")) : string(row);
            Long userId = businessUserId(account);
            if (userId == null) continue;
            ChatConversationMember existing = memberMapper.findByConversationAndUser(conversation.getId(), userId);
            if (existing != null && "ACTIVE".equals(existing.getMemberStatus())) continue;
            LocalDateTime now = LocalDateTime.now();
            if (existing != null) {
                memberMapper.reactivate(conversation.getId(), userId, "MEMBER", now);
                continue;
            }
            ChatConversationMember member = new ChatConversationMember();
            member.setId(SnowflakeIdGenerator.nextId());
            member.setConversationId(conversation.getId());
            member.setUserId(userId);
            member.setMemberRole("MEMBER");
            member.setMemberStatus("ACTIVE");
            member.setUnreadCount(0);
            member.setMutedFlag(false);
            member.setPinnedFlag(false);
            member.setJoinedAt(now);
            member.setCreatedAt(now);
            member.setUpdatedAt(now);
            memberMapper.insert(member);
        }
    }

    /** 成员主动退出腾讯群后，同步本地成员状态。 */
    private void syncMemberExit(Map<String, Object> body) {
        ChatConversation conversation = conversationMapper.findByProviderConversationKey(string(body.get("GroupId")));
        if (conversation == null) return;
        Object exit = body.get("ExitMemberList");
        if (!(exit instanceof List<?> list)) return;
        for (Object row : list) {
            String account = row instanceof Map<?, ?> map ? string(map.get("Member_Account")) : string(row);
            Long userId = businessUserId(account);
            if (userId != null) memberMapper.exit(conversation.getId(), userId, LocalDateTime.now());
        }
    }

    /** 腾讯群被销毁后归档本地业务会话，避免 App 继续展示可操作入口。 */
    private void syncGroupDestroyed(Map<String, Object> body) {
        ChatConversation conversation = conversationMapper.findByProviderConversationKey(string(body.get("GroupId")));
        if (conversation == null) return;
        LocalDateTime now = LocalDateTime.now();
        conversationMapper.archive(conversation.getId(), now);
        memberMapper.exitAll(conversation.getId(), now);
    }

    /** 把腾讯 IM MsgBody 转为本地统一 JSON，并为所有官方消息类型生成可读摘要。 */
    private Map<String, Object> normalizeBody(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return messageSummary("UNKNOWN", "[消息]");
        }
        // MsgBody 允许组合多个元素。优先返回第一个可识别元素，避免首元素异常时
        // 把后续正常文本、图片或文件错误显示为“不支持的消息”。
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> elem)) continue;
            String msgType = string(elem.get("MsgType"));
            Object contentRaw = elem.get("MsgContent");
            Map<?, ?> content = contentRaw instanceof Map<?, ?> map ? map : Map.of();
            if ("TIMTextElem".equals(msgType)) {
                return messageSummary("TEXT", string(content.get("Text")));
            }
            if ("TIMCustomElem".equals(msgType)) {
                String data = string(content.get("Data"));
                try {
                    Map<String, Object> custom = objectMapper.readValue(data, new TypeReference<>() { });
                    Map<String, Object> result = new LinkedHashMap<>(custom);
                    result.put("messageType", string(custom.getOrDefault("type", "CUSTOM")));
                    result.putIfAbsent("content", string(content.get("Desc")));
                    return result;
                } catch (Exception ignored) {
                    Map<String, Object> result = messageSummary("CUSTOM", string(content.get("Desc")));
                    result.put("rawData", data);
                    return result;
                }
            }
            if ("TIMLocationElem".equals(msgType)) {
                Map<String, Object> result = messageSummary("LOCATION", string(content.get("Desc")));
                result.put("latitude", content.get("Latitude"));
                result.put("longitude", content.get("Longitude"));
                return result;
            }
            if ("TIMImageElem".equals(msgType)) return messageSummary("IMAGE", "[图片]");
            if ("TIMFileElem".equals(msgType)) {
                String fileName = string(content.get("FileName"));
                return messageSummary("FILE", StringUtils.hasText(fileName) ? fileName : "[文件]");
            }
            if ("TIMSoundElem".equals(msgType)) return messageSummary("SOUND", "[语音]");
            if ("TIMVideoFileElem".equals(msgType)) return messageSummary("VIDEO", "[视频]");
            if ("TIMFaceElem".equals(msgType)) return messageSummary("FACE", "[表情]");
            if ("TIMRelayElem".equals(msgType)) return messageSummary("MERGER", "[聊天记录]");
        }
        return messageSummary("UNKNOWN", "[消息]");
    }

    private Map<String, Object> messageSummary(String messageType, String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("messageType", messageType);
        result.put("content", StringUtils.hasText(content) ? content : "[消息]");
        return result;
    }

    /** 读取旧后端发送接口使用的随机号关联键。 */
    private String legacyProviderMessageKey(Map<String, Object> body) {
        String random = StringUtils.hasText(string(body.get("MsgRandom")))
                ? string(body.get("MsgRandom")) : string(body.get("Random"));
        return StringUtils.hasText(random) ? "tencent-" + random : null;
    }

    private String providerMessageKey(String command, Map<String, Object> body) {
        String scope = StringUtils.hasText(string(body.get("GroupId")))
                ? string(body.get("GroupId"))
                : string(body.get("From_Account")) + "->" + string(body.get("To_Account"));
        String msgKey = string(body.get("MsgKey"));
        if (StringUtils.hasText(msgKey)) return command + ':' + scope + ':' + msgKey;
        String msgId = string(body.get("MsgId"));
        if (StringUtils.hasText(msgId)) return command + ':' + scope + ':' + msgId;
        // MsgSeq/MsgRandom 只在同一群或同一对账号范围内唯一，因此必须把会话范围一起纳入幂等键。
        return command + ':' + scope + ':' + string(body.get("MsgSeq")) + ':'
                + string(body.get("MsgRandom"));
    }

    private LocalDateTime callbackTime(Object value) {
        try {
            return LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(string(value))), ZoneId.systemDefault());
        } catch (Exception ignored) {
            return LocalDateTime.now();
        }
    }

    private Long businessUserId(String imUserId) {
        if (!StringUtils.hasText(imUserId) || !imUserId.startsWith("u_")) return null;
        try {
            return Long.valueOf(imUserId.substring(2));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String preview(String type, Map<String, Object> payload) {
        return switch (type) {
            case "TEXT" -> string(payload.get("content"));
            case "CHAT_IMAGE", "IMAGE" -> "[图片]";
            case "CHAT_FILE", "FILE" -> "[文件]";
            case "LOCATION" -> "[位置]";
            default -> StringUtils.hasText(string(payload.get("title")))
                    ? string(payload.get("title")) : "[自定义消息]";
        };
    }

    /** 保存发送后回调命中的风险事实，供管理端审核。 */
    private void persistRisk(Long messageId, RiskDecision decision) {
        LocalDateTime now = LocalDateTime.now();
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

    /** 轻量本地规则：高风险在发送前拒绝，中风险发送后进入审核。 */
    private RiskDecision detectRisk(String content) {
        String text = string(content).toLowerCase();
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
            if (content.contains(keyword)) return true;
        }
        return false;
    }

    private record RiskDecision(String level, String type, int confidence, String rule, boolean blocked) { }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) builder.append(String.format("%02x", b));
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "腾讯 IM 回调消息格式错误");
        }
    }

    private Long longValue(Object value) {
        if (value == null || !StringUtils.hasText(value.toString())) return null;
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
