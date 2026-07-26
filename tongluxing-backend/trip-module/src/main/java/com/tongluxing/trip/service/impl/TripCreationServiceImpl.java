package com.tongluxing.trip.service.impl;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;
import com.tongluxing.map.dto.LocationDto;
import com.tongluxing.map.dto.RoutePlanRequest;
import com.tongluxing.map.service.MapService;
import com.tongluxing.map.vo.RoutePlanResponse;
import com.tongluxing.trip.dto.CreateTripRequest;
import com.tongluxing.trip.dto.LocationRequest;
import com.tongluxing.trip.dto.TripDraftSaveRequest;
import com.tongluxing.trip.dto.TripWaypointCommand;
import com.tongluxing.trip.dto.TripWaypointOrderRequest;
import com.tongluxing.trip.dto.WaypointLocationRequest;
import com.tongluxing.trip.entity.TripCreationDraft;
import com.tongluxing.trip.entity.TripRoute;
import com.tongluxing.trip.entity.TripWaypoint;
import com.tongluxing.trip.integration.TripVehiclePort;
import com.tongluxing.trip.integration.TripVehiclePort.TripVehicleDTO;
import com.tongluxing.trip.mapper.TripCreationDraftMapper;
import com.tongluxing.trip.mapper.TripDraftMapper;
import com.tongluxing.trip.mapper.TripRouteMapper;
import com.tongluxing.trip.mapper.TripWaypointMapper;
import com.tongluxing.trip.service.TripCreationService;
import com.tongluxing.trip.service.TripService;
import com.tongluxing.trip.vo.LocationResponse;
import com.tongluxing.trip.vo.TripCreationWaypointResponse;
import com.tongluxing.trip.vo.TripDraftDetailResponse;
import com.tongluxing.trip.vo.TripDraftPublishResponse;
import com.tongluxing.trip.vo.TripDraftRouteResponse;
import com.tongluxing.trip.vo.TripResponse;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/** 创建行程草稿、路线、经停点和发布事务实现。 */
@Service
@RequiredArgsConstructor
public class TripCreationServiceImpl implements TripCreationService {
    private static final Set<String> WAYPOINT_TYPES = Set.of("MEETING", "REST", "HOTEL", "FUEL", "CHARGING", "CHECK_IN", "NORMAL");
    private final CurrentUserContext currentUserContext;
    private final ObjectMapper objectMapper;
    private final TripCreationDraftMapper draftMapper;
    private final TripDraftMapper legacyDraftMapper;
    private final TripWaypointMapper waypointMapper;
    private final TripRouteMapper routeMapper;
    private final MapService mapService;
    private final TripVehiclePort vehiclePort;
    private final TripService tripService;

    @Override
    @Transactional
    public TripDraftDetailResponse createDraft(TripDraftSaveRequest request) {
        long userId = currentUserContext.requireUserId();
        long id = SnowflakeIdGenerator.nextId();
        LocalDateTime now = LocalDateTime.now();
        draftMapper.insertEmpty(id, userId, now);
        if (request != null) updateDraftEntity(requireDraft(id, userId, false), request);
        return detail(requireDraft(id, userId, false));
    }

    @Override
    public TripDraftDetailResponse getDraft(Long draftId) {
        return detail(requireDraft(draftId, currentUserContext.requireUserId(), false));
    }

    @Override
    public List<TripDraftDetailResponse> listDrafts(String status) {
        String value = StringUtils.hasText(status) ? status.trim().toUpperCase() : "DRAFT";
        if (!Set.of("DRAFT", "PUBLISHED").contains(value)) throw bad("草稿状态不正确");
        if ("DRAFT".equals(value)) cleanupExpiredDrafts();
        return draftMapper.findByStatus(currentUserContext.requireUserId(), value).stream().map(this::detail).toList();
    }

    @Override
    @Transactional
    public TripDraftDetailResponse updateDraft(Long draftId, TripDraftSaveRequest request) {
        TripCreationDraft draft = requireEditableDraft(draftId);
        String oldStart = draft.getStartLocationJson();
        String oldEnd = draft.getEndLocationJson();
        updateDraftEntity(draft, request);
        if (!java.util.Objects.equals(oldStart, draft.getStartLocationJson())
                || !java.util.Objects.equals(oldEnd, draft.getEndLocationJson())) {
            routeMapper.markDraftRouteStale(draftId, LocalDateTime.now());
        }
        return detail(requireDraft(draftId, draft.getUserId(), false));
    }

    @Override
    @Transactional
    public void deleteDraft(Long draftId) {
        long userId = currentUserContext.requireUserId();
        if (draftMapper.softDelete(draftId, userId, LocalDateTime.now()) == 0) {
            throw notFound("行程草稿不存在或已发布");
        }
    }

    @Override
    @Transactional
    public int cleanupExpiredDrafts() {
        return draftMapper.softDeleteExpired(LocalDateTime.now().minusDays(7));
    }

    private void updateDraftEntity(TripCreationDraft draft, TripDraftSaveRequest request) {
        LocationRequest effectiveStart = request.startLocation() != null
                ? request.startLocation() : location(draft.getStartLocationJson());
        LocationRequest effectiveEnd = request.destination() != null
                ? request.destination() : location(draft.getEndLocationJson());
        // 草稿保存接口与经停点是分步提交的，这里只校验起终点；
        // 经停点冲突在新增/修改、路线规划和发布阶段再次强校验。
        validateEndpoints(effectiveStart, effectiveEnd);

        if (request.title() != null) draft.setTitle(text(request.title()));
        if (request.description() != null) draft.setDescription(text(request.description()));
        if (request.coverImageKey() != null) draft.setCoverImageKey(text(request.coverImageKey()));
        if (request.startTime() != null) draft.setDepartureTime(parseTime(request.startTime()));
        if (request.startLocation() != null) draft.setStartLocationJson(json(request.startLocation()));
        if (request.destination() != null) draft.setEndLocationJson(json(request.destination()));
        if (request.expectPeople() != null) draft.setPeopleCount(request.expectPeople());
        if (request.durationDays() != null) draft.setDurationDays(request.durationDays());
        if (request.vehicleRequirements() != null) {
            draft.setVehicleRequirements(vehicleRequirements(request.vehicleRequirements()));
        }
        if (request.budgetDescription() != null) draft.setBudgetDescription(text(request.budgetDescription()));
        if (request.notes() != null) draft.setNotes(text(request.notes()));
        draft.setUpdatedAt(LocalDateTime.now());
        if (draftMapper.update(draft) == 0) throw new BusinessException(409, "草稿状态不允许修改");
    }

    @Override
    @Transactional
    public TripCreationWaypointResponse addWaypoint(Long draftId, TripWaypointCommand request) {
        TripCreationDraft draft = requireEditableDraft(draftId);
        List<TripWaypoint> values = waypointMapper.findByDraftId(draftId);
        if (values.size() >= 5) throw bad("经停点最多 5 个");
        ensureWaypointUnique(draft, values, request, null);
        TripWaypoint waypoint = waypoint(draftId, null, request,
                request.sort() == null ? values.size() + 1 : request.sort());
        waypointMapper.insert(waypoint);
        normalizeSequence(draftId);
        routeMapper.markDraftRouteStale(draftId, LocalDateTime.now());
        return response(waypointMapper.findDraftWaypoint(draftId, waypoint.getId()));
    }

    @Override
    @Transactional
    public TripCreationWaypointResponse updateWaypoint(Long draftId, Long waypointId, TripWaypointCommand request) {
        TripWaypoint current = waypointMapper.findDraftWaypoint(draftId, waypointId);
        if (current == null) throw notFound("经停点不存在");
        TripCreationDraft draft = requireEditableDraft(draftId);
        ensureWaypointUnique(draft, waypointMapper.findByDraftId(draftId), request, waypointId);
        TripWaypoint waypoint = waypoint(draftId, waypointId, request,
                request.sort() == null ? current.getSeqNo() : request.sort());
        waypoint.setCreatedAt(current.getCreatedAt());
        if (waypointMapper.updateDraftWaypoint(waypoint) == 0) throw notFound("经停点不存在");
        normalizeSequence(draftId);
        routeMapper.markDraftRouteStale(draftId, LocalDateTime.now());
        return response(waypointMapper.findDraftWaypoint(draftId, waypointId));
    }

    @Override
    @Transactional
    public void deleteWaypoint(Long draftId, Long waypointId) {
        requireEditableDraft(draftId);
        if (waypointMapper.deleteDraftWaypoint(draftId, waypointId, LocalDateTime.now()) == 0) {
            throw notFound("经停点不存在");
        }
        normalizeSequence(draftId);
        routeMapper.markDraftRouteStale(draftId, LocalDateTime.now());
    }

    @Override
    @Transactional
    public List<TripCreationWaypointResponse> reorderWaypoints(Long draftId, TripWaypointOrderRequest request) {
        requireEditableDraft(draftId);
        List<TripWaypoint> values = waypointMapper.findByDraftId(draftId);
        Set<Long> existing = values.stream().map(TripWaypoint::getId).collect(java.util.stream.Collectors.toSet());
        Set<Long> requested = new HashSet<>(request.waypointIds());
        if (requested.size() != request.waypointIds().size() || !existing.equals(requested)) {
            throw bad("排序必须包含当前草稿的全部经停点且不能重复");
        }
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < request.waypointIds().size(); i++) {
            waypointMapper.updateDraftSequence(draftId, request.waypointIds().get(i), i + 1, now);
        }
        routeMapper.markDraftRouteStale(draftId, now);
        return waypointResponses(draftId);
    }

    @Override
    @Transactional
    public TripDraftRouteResponse planRoute(Long draftId) {
        TripCreationDraft draft = requireEditableDraft(draftId);
        LocationRequest start = location(draft.getStartLocationJson());
        LocationRequest end = location(draft.getEndLocationJson());
        if (start == null || end == null) throw bad("请先选择起点和终点");
        List<TripWaypoint> waypoints = waypointMapper.findByDraftId(draftId);
        validateRouteLocations(start, end, waypoints);
        RoutePlanResponse plan = mapService.planRoute(new RoutePlanRequest(dto(start), dto(end),
                waypoints.stream().map(this::dto).toList()));
        LocalDateTime now = LocalDateTime.now();
        TripRoute route = new TripRoute();
        route.setId(SnowflakeIdGenerator.nextId());
        route.setDraftId(draftId);
        route.setRoutePlanId(Long.valueOf(plan.routePlanId()));
        route.setOrigin(json(start));
        route.setDestination(json(end));
        route.setWaypoints(json(waypoints.stream().map(this::waypointLocation).toList()));
        route.setPolyline(plan.routePolyline());
        route.setPlanDistance(plan.routeDistance());
        route.setPlanDuration(plan.routeDuration());
        route.setProviderType(plan.providerType());
        route.setRouteStatus("VALID");
        route.setCreatedAt(now);
        route.setUpdatedAt(now);
        route.setDeleted(0);
        if (routeMapper.updateByDraftId(route) == 0) routeMapper.insert(route);
        return routeResponse(routeMapper.findByDraftId(draftId), start, end, waypoints);
    }

    @Override
    public TripDraftRouteResponse getRoute(Long draftId) {
        TripCreationDraft draft = requireDraft(draftId, currentUserContext.requireUserId(), false);
        TripRoute route = routeMapper.findByDraftId(draftId);
        if (route == null) return null;
        return routeResponse(route, location(draft.getStartLocationJson()), location(draft.getEndLocationJson()),
                waypointMapper.findByDraftId(draftId));
    }

    @Override
    @Transactional
    public TripDraftPublishResponse publish(Long draftId) {
        long userId = currentUserContext.requireUserId();
        TripCreationDraft draft = requireDraft(draftId, userId, true);
        if ("PUBLISHED".equals(draft.getDraftStatus()) && draft.getPublishedTripId() != null) {
            return new TripDraftPublishResponse(String.valueOf(draftId), String.valueOf(draft.getPublishedTripId()), "PUBLISHED");
        }
        validatePublish(draft);
        TripRoute route = routeMapper.findByDraftId(draftId);
        List<TripWaypoint> waypoints = waypointMapper.findByDraftId(draftId);
        TripVehicleDTO vehicle = vehiclePort.getDefaultCertifiedVehicle(userId);
        if (vehicle == null) throw new BusinessException(ResultCode.FORBIDDEN, "请先完成车辆认证");
        LocationRequest start = location(draft.getStartLocationJson());
        LocationRequest end = location(draft.getEndLocationJson());
        TripResponse trip = tripService.createTrip(new CreateTripRequest(vehicle.vehicleId(), draft.getTitle(),
                draft.getDescription(), draft.getCoverImageKey(), draft.getPeopleCount(), start, end,
                start.name() + " - " + end.name(), formatTime(draft.getDepartureTime()), draft.getDurationDays(),
                route.getPlanDistance(), route.getPlanDuration(), route.getPolyline(), draft.getPeopleCount(),
                "MIDDLE", true, vehicleRequirementsList(draft.getVehicleRequirements()),
                draft.getBudgetDescription(), draft.getNotes(), waypoints.stream().map(this::waypointLocation).toList()));
        long tripId = Long.parseLong(trip.tripId());
        LocalDateTime now = LocalDateTime.now();
        for (TripWaypoint value : waypoints) {
            TripWaypoint formal = waypoint(null, tripId, value);
            formal.setCreatedAt(now);
            formal.setUpdatedAt(now);
            waypointMapper.insert(formal);
        }
        if (legacyDraftMapper.markPublished(draftId, userId, tripId, "trip-create:" + draftId, now) == 0) {
            throw new BusinessException(409, "草稿已被其他请求发布");
        }
        return new TripDraftPublishResponse(String.valueOf(draftId), String.valueOf(tripId), "PUBLISHED");
    }

    private void validatePublish(TripCreationDraft draft) {
        if (!"DRAFT".equals(draft.getDraftStatus())) throw new BusinessException(409, "草稿状态不允许发布");
        if (!StringUtils.hasText(draft.getTitle())) throw bad("行程标题必填");
        if (draft.getDepartureTime() == null) throw bad("出发时间必填");
        if (!draft.getDepartureTime().isAfter(LocalDateTime.now())) {
            throw bad("出发时间必须晚于当前时间");
        }
        LocationRequest start = location(draft.getStartLocationJson());
        LocationRequest end = location(draft.getEndLocationJson());
        if (start == null) throw bad("起点必填");
        if (end == null) throw bad("终点必填");
        validateRouteLocations(start, end, waypointMapper.findByDraftId(draft.getId()));
        if (draft.getPeopleCount() == null) throw bad("预计人数必填");
        TripRoute route = routeMapper.findByDraftId(draft.getId());
        if (route == null || !"VALID".equals(route.getRouteStatus())) throw bad("请重新生成有效路线");
        if (waypointMapper.findByDraftId(draft.getId()).stream().anyMatch(v -> !WAYPOINT_TYPES.contains(v.getWaypointType()))) {
            throw bad("经停点类型不正确");
        }
    }


    private void validateEndpoints(LocationRequest start, LocationRequest end) {
        if (start != null && end != null && sameLocation(start, end)) {
            throw bad("起点和终点不能选择同一地点");
        }
    }

    /** 对完整路线做统一唯一性校验。 */
    private void validateDraftLocations(LocationRequest start, LocationRequest end, List<TripWaypoint> waypoints) {
        validateEndpoints(start, end);
        for (int i = 0; i < waypoints.size(); i++) {
            TripWaypoint current = waypoints.get(i);
            if (start != null && sameLocation(start, current)) throw bad("起点不能与经停点重复");
            if (end != null && sameLocation(end, current)) throw bad("终点不能与经停点重复");
            for (int j = i + 1; j < waypoints.size(); j++) {
                if (sameLocation(current, waypoints.get(j))) throw bad("经停点不能重复选择");
            }
        }
    }

    private void ensureWaypointUnique(TripCreationDraft draft, List<TripWaypoint> existing,
                                      TripWaypointCommand request, Long excludedWaypointId) {
        LocationRequest candidate = new LocationRequest(
                request.name(), request.address(), request.latitude(), request.longitude());
        LocationRequest start = location(draft.getStartLocationJson());
        LocationRequest end = location(draft.getEndLocationJson());
        if (start != null && sameLocation(candidate, start)) throw bad("经停点不能与起点重复");
        if (end != null && sameLocation(candidate, end)) throw bad("经停点不能与终点重复");
        for (TripWaypoint value : existing) {
            if (excludedWaypointId != null && excludedWaypointId.equals(value.getId())) continue;
            if (sameLocation(candidate, value)) throw bad("经停点不能重复选择");
        }
    }

    private void validateRouteLocations(LocationRequest start, LocationRequest end, List<TripWaypoint> waypoints) {
        validateDraftLocations(start, end, waypoints);
    }

    private boolean sameLocation(LocationRequest left, LocationRequest right) {
        if (left == null || right == null) return false;
        if (near(left.latitude(), right.latitude()) && near(left.longitude(), right.longitude())) return true;
        return sameText(left.name(), right.name())
                && StringUtils.hasText(left.address())
                && sameText(left.address(), right.address());
    }

    private boolean sameLocation(LocationRequest left, TripWaypoint right) {
        if (left == null || right == null) return false;
        if (near(left.latitude(), right.getLat()) && near(left.longitude(), right.getLng())) return true;
        return sameText(left.name(), right.getPlaceName())
                && StringUtils.hasText(left.address())
                && sameText(left.address(), right.getPlaceAddress());
    }

    private boolean sameLocation(TripWaypoint left, TripWaypoint right) {
        if (left == null || right == null) return false;
        if (near(left.getLat(), right.getLat()) && near(left.getLng(), right.getLng())) return true;
        return sameText(left.getPlaceName(), right.getPlaceName())
                && StringUtils.hasText(left.getPlaceAddress())
                && sameText(left.getPlaceAddress(), right.getPlaceAddress());
    }

    private boolean near(BigDecimal left, BigDecimal right) {
        return left != null && right != null
                && left.subtract(right).abs().compareTo(new BigDecimal("0.000001")) <= 0;
    }

    private boolean sameText(String left, String right) {
        return StringUtils.hasText(left) && StringUtils.hasText(right)
                && left.trim().replaceAll("\\s+", "").equalsIgnoreCase(right.trim().replaceAll("\\s+", ""));
    }

    private TripCreationDraft requireEditableDraft(Long draftId) {
        TripCreationDraft draft = requireDraft(draftId, currentUserContext.requireUserId(), false);
        if (!"DRAFT".equals(draft.getDraftStatus())) throw new BusinessException(409, "草稿状态不允许修改");
        return draft;
    }

    private TripCreationDraft requireDraft(Long draftId, Long userId, boolean lock) {
        TripCreationDraft draft = lock ? draftMapper.findForUpdate(draftId, userId) : draftMapper.find(draftId, userId);
        if (draft == null) throw notFound("行程草稿不存在");
        return draft;
    }

    private TripDraftDetailResponse detail(TripCreationDraft draft) {
        List<TripWaypoint> waypoints = waypointMapper.findByDraftId(draft.getId());
        TripRoute route = routeMapper.findByDraftId(draft.getId());
        LocationRequest start = location(draft.getStartLocationJson());
        LocationRequest end = location(draft.getEndLocationJson());
        return new TripDraftDetailResponse(String.valueOf(draft.getId()), draft.getTitle(), formatTime(draft.getDepartureTime()),
                response(start), response(end), draft.getDescription(), draft.getCoverImageKey(),
                draft.getPeopleCount(), draft.getDurationDays(),
                vehicleRequirementsList(draft.getVehicleRequirements()), draft.getBudgetDescription(), draft.getNotes(),
                draft.getDraftStatus(), draft.getPublishedTripId() == null ? "" : String.valueOf(draft.getPublishedTripId()),
                waypoints.stream().map(this::response).toList(),
                route == null ? null : routeResponse(route, start, end, waypoints), formatTime(draft.getUpdatedAt()));
    }

    private TripDraftRouteResponse routeResponse(TripRoute route, LocationRequest start, LocationRequest end,
                                                   List<TripWaypoint> waypoints) {
        return new TripDraftRouteResponse(String.valueOf(route.getDraftId()), String.valueOf(route.getRoutePlanId()),
                response(start), response(end), waypoints.stream().map(this::response).toList(), route.getPolyline(),
                route.getPlanDistance(), route.getPlanDuration(), route.getProviderType(), route.getRouteStatus());
    }

    private TripWaypoint waypoint(Long draftId, Long id, TripWaypointCommand request, int sort) {
        TripWaypoint value = new TripWaypoint();
        value.setId(id == null ? SnowflakeIdGenerator.nextId() : id);
        value.setDraftId(draftId);
        value.setSeqNo(sort);
        value.setPlaceName(text(request.name()));
        value.setPlaceAddress(text(request.address()));
        value.setWaypointType(normalizeWaypointType(request.type()));
        value.setLng(request.longitude());
        value.setLat(request.latitude());
        value.setStayMinutes(normalizeStayMinutes(request.stayMinutes()));
        value.setRemark(normalizeWaypointRemark(request.remark()));
        value.setCreatedAt(LocalDateTime.now());
        value.setUpdatedAt(LocalDateTime.now());
        value.setDeleted(0);
        return value;
    }

    private TripWaypoint waypoint(Long draftId, Long tripId, TripWaypoint source) {
        TripWaypoint value = new TripWaypoint();
        value.setId(SnowflakeIdGenerator.nextId()); value.setDraftId(draftId); value.setTripId(tripId);
        value.setSeqNo(source.getSeqNo()); value.setPlaceName(source.getPlaceName());
        value.setPlaceAddress(source.getPlaceAddress()); value.setWaypointType(normalizeWaypointType(source.getWaypointType()));
        value.setLat(source.getLat()); value.setLng(source.getLng()); value.setStayMinutes(normalizeStayMinutes(source.getStayMinutes()));
        value.setRemark(normalizeWaypointRemark(source.getRemark())); value.setDeleted(0); return value;
    }

    private WaypointLocationRequest waypointLocation(TripWaypoint value) {
        return new WaypointLocationRequest(value.getPlaceName(), value.getPlaceAddress(), value.getLat(), value.getLng(), value.getSeqNo());
    }

    private String normalizeWaypointType(String value) {
        if (!StringUtils.hasText(value)) return "NORMAL";
        String normalized = value.trim().toUpperCase();
        return switch (normalized) {
            case "MEETING", "REST", "HOTEL", "FUEL", "CHARGING", "CHECK_IN", "NORMAL" -> normalized;
            default -> "NORMAL";
        };
    }

    private Integer normalizeStayMinutes(Integer value) {
        if (value == null) return 0;
        return Math.max(0, Math.min(value, 1440));
    }

    private String normalizeWaypointRemark(String value) {
        if (!StringUtils.hasText(value)) return "";
        String normalized = value.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "").trim();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    private void normalizeSequence(Long draftId) {
        List<TripWaypoint> values = waypointMapper.findByDraftId(draftId);
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < values.size(); i++) waypointMapper.updateDraftSequence(draftId, values.get(i).getId(), i + 1, now);
    }

    private List<TripCreationWaypointResponse> waypointResponses(Long draftId) {
        return waypointMapper.findByDraftId(draftId).stream().map(this::response).toList();
    }

    private TripCreationWaypointResponse response(TripWaypoint value) {
        return new TripCreationWaypointResponse(String.valueOf(value.getId()), value.getPlaceName(), value.getPlaceAddress(),
                value.getLng(), value.getLat(), normalizeWaypointType(value.getWaypointType()), value.getSeqNo(),
                normalizeStayMinutes(value.getStayMinutes()), normalizeWaypointRemark(value.getRemark()));
    }

    private LocationResponse response(LocationRequest value) {
        return value == null ? null : new LocationResponse(value.name(), value.address(), value.latitude(), value.longitude());
    }

    private LocationDto dto(LocationRequest value) {
        return new LocationDto(value.name(), value.address(), value.latitude(), value.longitude());
    }

    private LocationDto dto(TripWaypoint value) {
        return new LocationDto(value.getPlaceName(), value.getPlaceAddress(), value.getLat(), value.getLng());
    }

    private LocationRequest location(String value) {
        if (!StringUtils.hasText(value)) return null;
        try { return objectMapper.readValue(value, LocationRequest.class); }
        catch (JsonProcessingException e) { throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "草稿位置数据损坏"); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw bad("请求数据无法序列化"); }
    }

    private LocalDateTime parseTime(String value) {
        if (!StringUtils.hasText(value)) return null;
        try { return LocalDateTime.parse(value.trim()); }
        catch (DateTimeParseException ignored) {
            try { return LocalDateTime.parse(value.trim(), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")); }
            catch (DateTimeParseException e) { throw bad("出发时间格式应为 yyyy-MM-dd HH:mm:ss"); }
        }
    }

    private String formatTime(LocalDateTime value) {
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    private String text(String value) { return value == null ? "" : value.trim(); }

    private String vehicleRequirements(List<String> values) {
        if (values == null || values.isEmpty() || values.stream().anyMatch("不限"::equals)) return "不限";
        List<String> allowed = List.of("SUV", "轿车", "越野车", "摩托车", "MPV", "新能源");
        List<String> normalized = values.stream().map(this::text).filter(allowed::contains).distinct().toList();
        if (normalized.isEmpty()) throw bad("请选择有效的车辆要求");
        return String.join(",", normalized);
    }

    private List<String> vehicleRequirementsList(String value) {
        if (!StringUtils.hasText(value)) return List.of("不限");
        return java.util.Arrays.stream(value.split(",")).map(String::trim).filter(v -> !v.isEmpty()).distinct().toList();
    }

    private BusinessException bad(String message) { return new BusinessException(ResultCode.BAD_REQUEST, message); }
    private BusinessException notFound(String message) { return new BusinessException(ResultCode.NOT_FOUND, message); }
}
