package com.tongdao.trip.service.impl;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;
import com.tongdao.trip.mapper.TripDraftMapper;
import com.tongdao.trip.dto.TripDraftQueryDTO;
import com.tongdao.trip.service.TripDraftPublishPort;
import com.tongdao.trip.service.TripDraftPublishPort.PublishOutcome;
import com.tongdao.trip.service.TripDraftService;
import com.tongdao.user.model.UserModels.LocationRequest;
import com.tongdao.user.model.UserModels.LocationVO;
import com.tongdao.user.model.UserModels.PageResult;
import com.tongdao.user.model.UserModels.PublishResultVO;
import com.tongdao.user.model.UserModels.TeamMatchVO;
import com.tongdao.user.model.UserModels.TripDraftRequest;
import com.tongdao.user.model.UserModels.TripDraftVO;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 行程草稿业务服务实现，负责草稿增删改查、发布和推荐车队查询。
 */
@Service
@RequiredArgsConstructor
public class TripDraftServiceImpl implements TripDraftService {
    private final CurrentUserContext currentUserContext;
    private final TripDraftMapper mapper;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<TripDraftPublishPort> publishPort;

    /**
     * 创建行程草稿，并将位置和途经点序列化保存。
     */
    @Override
    @Transactional
    public TripDraftVO create(TripDraftRequest request) {
        long userId = currentUserContext.requireUserId();
        long id = SnowflakeIdGenerator.nextId();
        LocalDateTime now = LocalDateTime.now();
        mapper.insert(id, userId, json(request.startLocation()), json(request.endLocation()), json(list(request.waypoints())),
                request.departureTime(), request.durationDays(), request.peopleCount(), text(request.remark()), now);
        return draft(mapper.find(id, userId));
    }

    /**
     * 分页查询当前用户草稿，规范分页参数范围。
     */
    @Override
    public PageResult<TripDraftVO> list(String status, int page, int size) {
        long userId = currentUserContext.requireUserId();
        int p = Math.max(1, page), s = Math.min(100, Math.max(1, size));
        List<TripDraftVO> records = mapper.findAll(userId, status, (p - 1) * s, s).stream().map(this::draft).toList();
        return new PageResult<>(records, mapper.count(userId, status), p, s);
    }

    /**
     * 更新可编辑状态下的草稿。
     */
    @Override
    @Transactional
    public TripDraftVO update(Long id, TripDraftRequest request) {
        long userId = currentUserContext.requireUserId();
        int changed = mapper.update(id, userId, json(request.startLocation()), json(request.endLocation()), json(list(request.waypoints())),
                request.departureTime(), request.durationDays(), request.peopleCount(), text(request.remark()), LocalDateTime.now());
        if (changed == 0) throw new BusinessException(409, "草稿不存在或状态不允许修改");
        return draft(mapper.find(id, userId));
    }

    /**
     * 删除可删除状态下的草稿。
     */
    @Override
    @Transactional
    public void delete(Long id) {
        if (mapper.delete(id, currentUserContext.requireUserId(), LocalDateTime.now()) == 0) {
            throw new BusinessException(409, "草稿不存在或状态不允许删除");
        }
    }

    /**
     * 发布草稿；实际发布动作通过 TripDraftPublishPort 交给应用层适配器完成。
     */
    @Override
    @Transactional
    public PublishResultVO publish(Long id, String publishType) {
        long userId = currentUserContext.requireUserId();
        TripDraftVO value = draft(mapper.findForUpdate(id, userId));
        if (value == null) throw new BusinessException(ResultCode.NOT_FOUND, "行程草稿不存在");
        if (!"DRAFT".equals(value.draftStatus())) throw new BusinessException(409, "草稿状态不允许发布");
        TripDraftPublishPort port = publishPort.getIfAvailable();
        if (port == null) throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "行程发布服务暂不可用");
        PublishOutcome outcome = port.publish(userId, value, publishType, "trip-draft:" + id);
        if (outcome == null || outcome.tripId() == null || outcome.publishedId() == null
                || mapper.markPublished(id, userId, outcome.tripId(), "trip-draft:" + id, LocalDateTime.now()) == 0) {
            throw new BusinessException(409, "草稿已被其他请求处理");
        }
        return new PublishResultVO(id, publishType, outcome.publishedId());
    }

    /**
     * 基于草稿内容查询推荐车队；发布适配器不可用时返回空列表。
     */
    @Override
    public PageResult<TeamMatchVO> recommendTeams(Long id, int page, int size) {
        long userId = currentUserContext.requireUserId();
        TripDraftVO value = draft(mapper.find(id, userId));
        if (value == null) throw new BusinessException(ResultCode.NOT_FOUND, "行程草稿不存在");
        int p = Math.max(1, page), s = Math.min(100, Math.max(1, size));
        TripDraftPublishPort port = publishPort.getIfAvailable();
        List<TeamMatchVO> records = port == null ? List.of() : port.recommendTeams(userId, value, p, s);
        records = records == null ? List.of() : records;
        return new PageResult<>(records, records.size(), p, s);
    }

    /**
     * 将数据库查询行转换为草稿视图对象。
     */
    private TripDraftVO draft(TripDraftQueryDTO row) {
        if (row == null) return null;
        return new TripDraftVO(row.getDraftId(), location(row.getStartJson()), location(row.getEndJson()),
                locations(row.getWaypointJson()), row.getDepartureTime(), row.getDurationDays(),
                row.getPeopleCount(), row.getRemark(), row.getDraftStatus(), row.getPublishedTripId(), row.getUpdatedAt());
    }

    /**
     * 将草稿位置 JSON 反序列化为位置视图对象。
     */
    private LocationVO location(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            LocationRequest value = objectMapper.readValue(json, LocationRequest.class);
            return new LocationVO(value.name(), value.address(), value.latitude(), value.longitude());
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "草稿位置数据损坏");
        }
    }

    /**
     * 将草稿途经点 JSON 反序列化为位置视图列表。
     */
    private List<LocationVO> locations(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            List<LocationRequest> values = objectMapper.readValue(json, new TypeReference<>() { });
            return values.stream().map(v -> new LocationVO(v.name(), v.address(), v.latitude(), v.longitude())).toList();
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "草稿途经点数据损坏");
        }
    }

    /**
     * 将请求对象序列化为 JSON 字符串。
     */
    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new BusinessException(ResultCode.BAD_REQUEST, "请求数据无法序列化"); }
    }

    /**
     * 空列表兜底，避免保存 null 途经点。
     */
    private <T> List<T> list(List<T> value) { return value == null ? Collections.emptyList() : value; }

    /**
     * 清理备注等文本字段前后空格。
     */
    private String text(String value) { return value == null ? "" : value.trim(); }
}
