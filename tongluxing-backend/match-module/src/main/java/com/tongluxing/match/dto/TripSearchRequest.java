package com.tongluxing.match.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * “发现同行”高级行程搜索条件。
 *
 * <p>起点和终点同时使用名称与经纬度：名称用于展示和重复地点校验，经纬度用于计算
 * 与候选行程两端的球面距离。所有地点坐标使用 WGS84 合法范围。</p>
 *
 * @param startName 用户期望的起点名称
 * @param startLatitude 起点纬度，范围 -90~90
 * @param startLongitude 起点经度，范围 -180~180
 * @param endName 用户期望的终点名称
 * @param endLatitude 终点纬度
 * @param endLongitude 终点经度
 * @param waypoints 最多五个期望经停点
 * @param departureStart 可接受出发时间窗口起点
 * @param departureEnd 可接受出发时间窗口终点，必须晚于起点
 * @param radiusMeters 起终点允许偏差半径，默认 50 公里
 * @param timeToleranceMinutes 相对时间窗口中心的容忍分钟数
 * @param minimumRemainingSeats 最少剩余名额
 * @param vehicleType 可选车辆类型要求
 * @param carpoolAllowed 为 true 时只保留支持多人同行的车队
 * @param driverVerified 为 true 时只保留已认证发起人
 * @param sortBy MATCH_SCORE、DEPARTURE_TIME 或 DISTANCE
 * @param page 从 1 开始的页码
 * @param size 每页数量，最大 50
 */
public record TripSearchRequest(
        @NotBlank @Size(max = 128) String startName,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal startLatitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal startLongitude,
        @NotBlank @Size(max = 128) String endName,
        @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal endLatitude,
        @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal endLongitude,
        @Valid @Size(max = 5) List<LocationCondition> waypoints,
        @NotNull LocalDateTime departureStart,
        @NotNull LocalDateTime departureEnd,
        @Min(500) @Max(200000) Integer radiusMeters,
        @Min(0) @Max(1440) Integer timeToleranceMinutes,
        @Min(1) @Max(20) Integer minimumRemainingSeats,
        @Size(max = 32) String vehicleType,
        Boolean carpoolAllowed,
        Boolean driverVerified,
        @Size(max = 20) String sortBy,
        @Min(1) Integer page,
        @Min(1) @Max(50) Integer size
) {
    /**
     * 搜索条件中的经停地点。
     *
     * @param name 经停点名称，用于和候选行程途经点做规范化文本匹配
     * @param latitude 纬度，当前评分以名称匹配为主，坐标为后续路线算法保留
     * @param longitude 经度
     */
    public record LocationCondition(
            @NotBlank @Size(max = 128) String name,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude
    ) {
    }
}
