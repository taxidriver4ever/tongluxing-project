package com.tongluxing.groupbuy.service.impl;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.groupbuy.dto.CreateGroupbuyRequest;
import com.tongluxing.groupbuy.dto.PaidParticipantRequest;
import com.tongluxing.groupbuy.dto.JoinGroupbuyRequest;
import com.tongluxing.groupbuy.entity.GroupbuyActivity;
import com.tongluxing.groupbuy.entity.GroupbuyParticipant;
import com.tongluxing.groupbuy.integration.GroupbuyMerchantProductPort;
import com.tongluxing.groupbuy.mapper.GroupbuyActivityMapper;
import com.tongluxing.groupbuy.mapper.GroupbuyParticipantMapper;
import com.tongluxing.groupbuy.service.GroupbuyService;
import com.tongluxing.groupbuy.vo.GroupbuyActivityVO;
import com.tongluxing.groupbuy.vo.GroupbuyParticipantVO;
import com.tongluxing.groupbuy.vo.PageResult;
import com.tongluxing.user.support.CurrentUserContext;
import com.tongluxing.merchant.mapper.MerchantProfileMapper;
import com.tongluxing.merchant.dto.MerchantQueryDTO;

import lombok.RequiredArgsConstructor;

/**
 * 拼团活动业务服务实现。
 *
 * <p>以 MySQL 作为事实数据源，Redis 仅用于幂等结果和详情短缓存；Redis 读写失败时不阻断主流程。</p>
 */
@Service
@RequiredArgsConstructor
public class GroupbuyServiceImpl implements GroupbuyService {
    private final CurrentUserContext currentUserContext;
    private final GroupbuyMerchantProductPort merchantProductPort;
    private final GroupbuyActivityMapper activityMapper;
    private final GroupbuyParticipantMapper participantMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final MerchantProfileMapper merchantProfileMapper;

    @Override
    @Transactional
    public GroupbuyActivityVO create(CreateGroupbuyRequest request) {
        Long userId = currentUserContext.requireUserId();
        String idemKey = "groupbuy:idem:create:%s".formatted(request.requestId());
        GroupbuyActivityVO cached = readJson(idemKey, GroupbuyActivityVO.class);
        if (cached != null) {
            return cached;
        }
        LocalDateTime now = LocalDateTime.now();
        // 创建活动时读取商品快照，避免后续商品配置变化影响已创建的拼团价格与规则。
        Long sourceId = request.couponId() != null ? request.couponId() : request.productId();
        if (sourceId == null) throw new BusinessException(ResultCode.BAD_REQUEST, "couponId 不能为空");
        GroupbuyMerchantProductPort.MerchantProductSnapshot snapshot = merchantProductPort.getSnapshot(sourceId);
        if (!snapshot.available()) throw new BusinessException(409, "拼单优惠未通过审核、已下线或库存不足");
        Integer targetPeople = request.targetPeople() == null ? snapshot.targetPeople() : request.targetPeople();
        Integer validHours = request.validHours() == null ? snapshot.validHours() : request.validHours();
        if (targetPeople == null || targetPeople < 2 || validHours == null || validHours < 1) throw new BusinessException(ResultCode.BAD_REQUEST,"拼单规则不完整");
        merchantProductPort.decreaseStock(sourceId, 1, "groupbuy:create:" + request.requestId());

        GroupbuyActivity activity = new GroupbuyActivity();
        activity.setId(SnowflakeIdGenerator.nextId());
        activity.setMerchantId(snapshot.merchantId());
        activity.setProductId(sourceId);
        activity.setInitiatorUserId(userId);
        activity.setTargetPeople(targetPeople);
        activity.setCurrentPeople(1);
        activity.setGroupPrice(snapshot.groupPrice());
        activity.setLadderPriceJson(StringUtils.hasText(snapshot.ladderPriceJson()) ? snapshot.ladderPriceJson() : "[]");
        activity.setActivityStatus("WAITING");
        activity.setStartAt(now);
        activity.setExpireAt(now.plusHours(validHours));
        activity.setCreatedAt(now);
        activity.setUpdatedAt(now);
        activity.setDeleted(0);
        activityMapper.insert(activity);
        insertParticipant(activity.getId(), SnowflakeIdGenerator.nextId(), userId, now);
        GroupbuyActivityVO result = toVO(activityMapper.findById(activity.getId()));
        writeJson(idemKey, result, Duration.ofHours(24));
        return result;
    }

    @Override
    public PageResult<GroupbuyActivityVO> list(String status, int page, int size) {
        // 对分页参数做兜底，避免异常页码或超大 size 直接压到数据库。
        int normalizedPage = Math.max(page, 1);
        int normalizedSize = Math.min(Math.max(size, 1), 100);
        int offset = (normalizedPage - 1) * normalizedSize;
        String normalizedStatus = StringUtils.hasText(status) ? status.trim() : null;
        List<GroupbuyActivityVO> records = activityMapper.list(normalizedStatus, offset, normalizedSize)
                .stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(records, activityMapper.count(normalizedStatus), normalizedPage, normalizedSize);
    }

    @Override
    public PageResult<GroupbuyActivityVO> mine(String status,int page,int size){
        int p=Math.max(page,1),z=Math.min(Math.max(size,1),100),offset=(p-1)*z;
        String s=StringUtils.hasText(status)?status.trim().toUpperCase():null;
        Long userId=currentUserContext.requireUserId();
        return new PageResult<>(activityMapper.listMine(userId,s,offset,z).stream().map(this::toVO).toList(),activityMapper.countMine(userId,s),p,z);
    }

    @Override
    public PageResult<GroupbuyActivityVO> merchantActivities(String status,int page,int size){
        Long userId=currentUserContext.requireUserId();MerchantQueryDTO merchant=merchantProfileMapper.findByUserId(userId);
        if(merchant==null||!"APPROVED".equals(merchant.getAuditStatus())) throw new BusinessException(403,"仅审核通过的商家可查看拼单活动");
        int p=Math.max(page,1),z=Math.min(Math.max(size,1),100),offset=(p-1)*z;String s=StringUtils.hasText(status)?status.trim().toUpperCase():null;
        return new PageResult<>(activityMapper.listByMerchant(merchant.getMerchantId(),s,offset,z).stream().map(this::toVO).toList(),activityMapper.countByMerchant(merchant.getMerchantId(),s),p,z);
    }

    @Override
    public GroupbuyActivityVO detail(Long activityId) {
        String key = "groupbuy:cache:activity:%d".formatted(activityId);
        GroupbuyActivityVO cached = readJson(key, GroupbuyActivityVO.class);
        if (cached != null) {
            return cached;
        }
        GroupbuyActivityVO result = toVO(requireActivity(activityId));
        writeJson(key, result, Duration.ofMinutes(5));
        return result;
    }

    @Override
    @Transactional
    public GroupbuyActivityVO addPaidParticipant(Long activityId, PaidParticipantRequest request) {
        String idemKey = "groupbuy:idem:paid-participant:%s".formatted(request.requestId());
        GroupbuyActivityVO cached = readJson(idemKey, GroupbuyActivityVO.class);
        if (cached != null) {
            return cached;
        }
        GroupbuyActivity activity = requireActivity(activityId);
        if (!List.of("ONGOING","WAITING").contains(activity.getActivityStatus())) {
            return toVO(activity);
        }
        if (participantMapper.findByActivityAndUser(activityId, request.userId()) == null) {
            LocalDateTime now = LocalDateTime.now();
            GroupbuyParticipant participant = new GroupbuyParticipant();
            participant.setId(SnowflakeIdGenerator.nextId());
            participant.setActivityId(activityId);
            participant.setOrderId(request.orderId());
            participant.setUserId(request.userId());
            participant.setParticipantStatus("PAID");
            participant.setJoinedAt(now);
            participant.setPaidAt(request.paidAt() == null ? now : request.paidAt());
            participant.setCreatedAt(now);
            participant.setUpdatedAt(now);
            participant.setDeleted(0);
            participantMapper.insert(participant);
            activityMapper.increasePeople(activityId, now);
            activityMapper.markSuccessIfReached(activityId, now);
            redis.delete("groupbuy:cache:activity:%d".formatted(activityId));
        }
        GroupbuyActivityVO result = toVO(requireActivity(activityId));
        writeJson(idemKey, result, Duration.ofDays(7));
        return result;
    }

    @Override
    @Transactional
    public GroupbuyActivityVO expire(Long activityId) {
        LocalDateTime now = LocalDateTime.now();
        activityMapper.markFailedIfExpired(activityId, now);
        redis.delete("groupbuy:cache:activity:%d".formatted(activityId));
        return toVO(requireActivity(activityId));
    }

    /**
     * 执行运营后台干预动作，直接推进拼团状态。
     *
     * <p>该方法由 admin-module 通过端口适配调用。干预请求使用 requestId 做幂等，避免重复点击或重试导致状态反复写入。</p>
     */
    @Override
    @Transactional
    public GroupbuyActivityVO applyAdminIntervention(Long activityId, String action, String reason, String requestId, Integer extendMinutes) {
        String idemKey = "groupbuy:idem:admin-intervention:%s".formatted(requestId);
        GroupbuyActivityVO cached = readJson(idemKey, GroupbuyActivityVO.class);
        if (cached != null) {
            return cached;
        }
        String normalizedAction = action == null ? "" : action.trim().toUpperCase();
        LocalDateTime now = LocalDateTime.now();
        if ("FORCE_SUCCESS".equals(normalizedAction)) {
            activityMapper.forceSuccess(activityId, now);
        } else if (List.of("FORCE_FAIL","FORCE_FAILED").contains(normalizedAction)) {
            activityMapper.forceEnd(activityId, "FORCE_FAIL", now);
        } else if (List.of("CLOSE","OFFLINE").contains(normalizedAction)) {
            activityMapper.forceEnd(activityId, "CANCEL", now);
        } else if ("SUSPEND".equals(normalizedAction)) {
            activityMapper.suspend(activityId,now);
        } else if ("EXTEND".equals(normalizedAction)) {
            int minutes=extendMinutes==null?30:extendMinutes;
            if(minutes<1||minutes>1440) throw new BusinessException(ResultCode.BAD_REQUEST,"延长时间必须为1至1440分钟");
            activityMapper.extend(activityId,minutes,now);
        } else {
            throw new BusinessException("拼团干预动作不支持");
        }
        redis.delete("groupbuy:cache:activity:%d".formatted(activityId));
        GroupbuyActivityVO result = toVO(requireActivity(activityId));
        writeJson(idemKey, result, Duration.ofDays(7));
        return result;
    }

    @Override
    @Transactional
    public GroupbuyActivityVO join(Long activityId,JoinGroupbuyRequest request){
        Long userId=currentUserContext.requireUserId();
        return addPaidParticipant(activityId,new PaidParticipantRequest(SnowflakeIdGenerator.nextId(),userId,LocalDateTime.now(),request.requestId()));
    }

    /**
     * 查询活动并统一处理不存在的业务异常。
     */
    private GroupbuyActivity requireActivity(Long activityId) {
        GroupbuyActivity activity = activityMapper.findById(activityId);
        if (activity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "拼团活动不存在");
        }
        return activity;
    }

    /**
     * 将活动实体组装为接口响应对象，并附带当前活动的参与人列表。
     */
    private GroupbuyActivityVO toVO(GroupbuyActivity activity) {
        List<GroupbuyParticipantVO> participants = participantMapper.findByActivityId(activity.getId())
                .stream()
                .map(p -> new GroupbuyParticipantVO(p.getId(), p.getActivityId(), p.getOrderId(), p.getUserId(),
                        p.getParticipantStatus(), p.getJoinedAt(), p.getPaidAt(), p.getRefundedAt()))
                .toList();
        GroupbuyMerchantProductPort.MerchantProductSnapshot snapshot=merchantProductPort.getSnapshot(activity.getProductId());
        return new GroupbuyActivityVO(activity.getId(), activity.getMerchantId(), activity.getProductId(),
                snapshot.couponOffer()?activity.getProductId():null,snapshot.productName(),snapshot.merchantName(),
                snapshot.storeId(),snapshot.storeName(),snapshot.storeAddress(),snapshot.originalPrice(),
                activity.getInitiatorUserId(), activity.getTargetPeople(), activity.getCurrentPeople(),
                activity.getGroupPrice(), activity.getActivityStatus(), activity.getStartAt(), activity.getExpireAt(),
                activity.getSuccessAt(), activity.getFailedAt(), participants);
    }

    private void insertParticipant(Long activityId,Long orderId,Long userId,LocalDateTime now){
        GroupbuyParticipant participant=new GroupbuyParticipant();
        participant.setId(SnowflakeIdGenerator.nextId());participant.setActivityId(activityId);participant.setOrderId(orderId);
        participant.setUserId(userId);participant.setParticipantStatus("PAID");participant.setJoinedAt(now);participant.setPaidAt(now);
        participant.setCreatedAt(now);participant.setUpdatedAt(now);participant.setDeleted(0);participantMapper.insert(participant);
    }

    /**
     * 从 Redis 读取 JSON 缓存。
     *
     * <p>缓存只作为性能和幂等优化，反序列化失败时返回 null，由调用方继续走数据库逻辑。</p>
     */
    private <T> T readJson(String key, Class<T> type) {
        try {
            String value = redis.opsForValue().get(key);
            return value == null ? null : objectMapper.readValue(value, type);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 将对象序列化后写入 Redis，并设置过期时间。
     */
    private void writeJson(String key, Object value, Duration ttl) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
            // Redis 失败不影响 MySQL 事实数据。
        }
    }
}
