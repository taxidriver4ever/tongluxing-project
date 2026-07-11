package com.tongluxing.customerservice.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.customerservice.dto.AssignTicketRequest;
import com.tongluxing.customerservice.dto.CloseTicketRequest;
import com.tongluxing.customerservice.dto.CreateTicketRequest;
import com.tongluxing.customerservice.dto.CustomerServiceQueryDTO;
import com.tongluxing.customerservice.dto.ReplyTicketRequest;
import com.tongluxing.customerservice.mapper.CustomerServiceTicketMapper;
import com.tongluxing.customerservice.service.CustomerServiceTicketService;
import com.tongluxing.customerservice.vo.PageResult;
import com.tongluxing.customerservice.vo.TicketMessageVO;
import com.tongluxing.customerservice.vo.TicketVO;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 客服工单服务实现。
 *
 * <p>第三方微信客服未接入时，工单表和消息表承担客服事实数据：工单表记录处理状态和归属，
 * 消息表记录用户首问、系统自动回复和运营回复。后续接入微信客服后，可以把外部消息同步到同一套表。</p>
 *
 * <p>当前实现以 MySQL request_id 作为最终幂等依据，Redis 只缓存短期结果，加快重复提交返回速度。
 * 因此 Redis 故障不会导致重复工单，只会回落到 MySQL 查询。</p>
 */
@Service
@RequiredArgsConstructor
public class CustomerServiceTicketServiceImpl implements CustomerServiceTicketService {

    private static final String CREATOR_USER = "USER";
    private static final String SENDER_USER = "USER";
    private static final String SENDER_ADMIN = "ADMIN";
    private static final String SENDER_SYSTEM = "SYSTEM";
    private static final String MESSAGE_TEXT = "TEXT";
    private static final String IDEM_TICKET_KEY = "customer-service:idem:ticket:%s";
    private static final int MAX_PAGE_SIZE = 100;

    private final CurrentUserContext currentUserContext;
    private final CustomerServiceTicketMapper ticketMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public TicketVO createTicket(CreateTicketRequest request) {
        // 工单归属必须来自当前登录用户，不能信任前端传入用户 ID。
        Long userId = currentUserContext.requireUserId();
        String requestId = request.requestId().trim();

        // 先读 Redis 幂等缓存，重复点击时直接返回上一次创建结果。
        TicketVO cached = readJson(IDEM_TICKET_KEY.formatted(requestId), TicketVO.class);
        if (cached != null) {
            return cached;
        }

        // Redis 失效后继续按 MySQL request_id 回源，保证幂等语义不依赖缓存可用性。
        CustomerServiceQueryDTO existed = ticketMapper.findTicketByRequestId(requestId);
        if (existed != null) {
            TicketVO result = toTicketVO(existed);
            writeJson(IDEM_TICKET_KEY.formatted(requestId), result, Duration.ofHours(24));
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        long ticketId = SnowflakeIdGenerator.nextId();
        ticketMapper.insertTicket(
                ticketId,
                CREATOR_USER,
                userId,
                normalize(request.scene()),
                trimToEmpty(request.targetType()),
                trimToEmpty(request.targetId()),
                request.title().trim(),
                request.content().trim(),
                requestId,
                now);

        // 用户提交内容既是工单摘要，也是消息时间线中的首条消息。
        ticketMapper.insertMessage(
                SnowflakeIdGenerator.nextId(),
                ticketId,
                SENDER_USER,
                userId,
                MESSAGE_TEXT,
                request.content().trim(),
                writeImageKeys(request.imageKeys()),
                now);
        insertAutoReply(ticketId, request.scene(), now);
        TicketVO result = ticketDetail(ticketId);
        writeJson(IDEM_TICKET_KEY.formatted(requestId), result, Duration.ofHours(24));
        return result;
    }

    @Override
    public PageResult<TicketVO> listMyTickets(int page, int size) {
        Long userId = currentUserContext.requireUserId();
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<TicketVO> records = ticketMapper.listMyTickets(CREATOR_USER, userId, offset, normalizedSize)
                .stream()
                .map(this::toTicketVO)
                .toList();
        long total = ticketMapper.countMyTickets(CREATOR_USER, userId);
        return new PageResult<>(records, total, normalizedPage, normalizedSize);
    }

    @Override
    public TicketVO ticketDetail(Long ticketId) {
        Long userId = currentUserContext.requireUserId();
        CustomerServiceQueryDTO ticket = requireTicket(ticketId);
        // 用户侧详情接口只允许创建者查看自己的工单，避免互相查看投诉和订单信息。
        if (CREATOR_USER.equals(ticket.getCreatorType()) && !userId.equals(ticket.getCreatorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看该工单");
        }
        return toTicketVO(ticket);
    }

    @Override
    public PageResult<TicketVO> listAdminTickets(String status, int page, int size) {
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int offset = (normalizedPage - 1) * normalizedSize;
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        List<TicketVO> records = ticketMapper.listAdminTickets(normalizedStatus, offset, normalizedSize)
                .stream()
                .map(this::toTicketVO)
                .toList();
        long total = ticketMapper.countAdminTickets(normalizedStatus);
        return new PageResult<>(records, total, normalizedPage, normalizedSize);
    }

    @Override
    @Transactional
    public TicketVO reply(Long ticketId, ReplyTicketRequest request) {
        requireTicket(ticketId);
        LocalDateTime now = LocalDateTime.now();
        // 回复前先把工单推进到处理中，关闭状态会被 Mapper 拒绝，避免关闭后继续写消息。
        int changed = ticketMapper.markProcessing(ticketId, request.operatorId(), now);
        if (changed == 0) {
            throw new BusinessException("工单状态不允许回复");
        }
        ticketMapper.insertMessage(
                SnowflakeIdGenerator.nextId(),
                ticketId,
                SENDER_ADMIN,
                request.operatorId(),
                MESSAGE_TEXT,
                request.content().trim(),
                writeImageKeys(request.imageKeys()),
                now);
        return toTicketVO(requireTicket(ticketId));
    }

    @Override
    @Transactional
    public TicketVO assign(Long ticketId, AssignTicketRequest request) {
        requireTicket(ticketId);
        // 分配和回复共用 PROCESSING 状态，assigned_admin_id 记录当前处理人。
        int changed = ticketMapper.markProcessing(ticketId, request.operatorId(), LocalDateTime.now());
        if (changed == 0) {
            throw new BusinessException("工单状态不允许分配");
        }
        return toTicketVO(requireTicket(ticketId));
    }

    @Override
    @Transactional
    public TicketVO close(Long ticketId, CloseTicketRequest request) {
        requireTicket(ticketId);
        int changed = ticketMapper.closeTicket(ticketId, request.operatorId(), LocalDateTime.now());
        if (changed == 0) {
            throw new BusinessException("工单已关闭或不存在");
        }
        return toTicketVO(requireTicket(ticketId));
    }

    /**
     * 写入本地自动回复消息。
     *
     * <p>微信客服未接入前，先用系统消息承接用户预期；后续可替换为规则表匹配。</p>
     */
    private void insertAutoReply(Long ticketId, String scene, LocalDateTime now) {
        ticketMapper.insertMessage(
                SnowflakeIdGenerator.nextId(),
                ticketId,
                SENDER_SYSTEM,
                0L,
                MESSAGE_TEXT,
                autoReplyContent(scene),
                "[]",
                now);
    }

    /**
     * 根据场景生成 MVP 阶段自动回复文案。
     */
    private String autoReplyContent(String scene) {
        String normalizedScene = normalize(scene);
        if ("REFUND".equals(normalizedScene)) {
            return "已收到退款相关问题，平台会核对订单和支付状态后继续处理。";
        }
        if ("VERIFICATION".equals(normalizedScene) || "ORDER_DETAIL".equals(normalizedScene)) {
            return "已收到订单或核销相关问题，平台会协助核对商家履约记录。";
        }
        if ("COMPLAINT".equals(normalizedScene)) {
            return "已收到投诉，平台会尽快分配运营人员跟进。";
        }
        return "已收到您的问题，平台客服会尽快处理。";
    }

    /**
     * 查询工单，不存在时抛出统一业务异常。
     */
    private CustomerServiceQueryDTO requireTicket(Long ticketId) {
        CustomerServiceQueryDTO ticket = ticketMapper.findTicketById(ticketId);
        if (ticket == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "工单不存在");
        }
        return ticket;
    }

    /**
     * 将工单查询对象转换为接口返回模型，并补齐消息时间线。
     */
    private TicketVO toTicketVO(CustomerServiceQueryDTO ticket) {
        List<TicketMessageVO> messages = ticketMapper.findMessages(ticket.getId())
                .stream()
                .map(this::toMessageVO)
                .toList();
        return new TicketVO(
                ticket.getId(),
                ticket.getCreatorType(),
                ticket.getCreatorId(),
                ticket.getScene(),
                ticket.getTargetType(),
                ticket.getTargetId(),
                ticket.getTitle(),
                ticket.getContent(),
                ticket.getTicketStatus(),
                ticket.getPriority(),
                ticket.getAssignedAdminId(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getClosedAt(),
                messages);
    }

    /**
     * 将消息查询对象转换为接口返回模型。
     */
    private TicketMessageVO toMessageVO(CustomerServiceQueryDTO message) {
        Long ticketId = message.getTargetId() == null ? null : Long.valueOf(message.getTargetId());
        return new TicketMessageVO(
                message.getId(),
                ticketId,
                message.getSenderType(),
                message.getSenderId(),
                message.getMessageType(),
                message.getContent(),
                readImageKeys(message.getImageKeysJson()),
                message.getCreatedAt());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 将图片对象存储 Key 列表序列化到消息表。
     */
    private String writeImageKeys(List<String> imageKeys) {
        try {
            return objectMapper.writeValueAsString(imageKeys == null ? List.of() : imageKeys);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "图片附件无法序列化");
        }
    }

    /**
     * 读取历史图片附件；旧数据格式异常时返回空列表，避免影响工单详情展示。
     */
    private List<String> readImageKeys(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private <T> T readJson(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeJson(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // Redis 幂等缓存失败时仍有 MySQL requestId 兜底。
        }
    }
}
