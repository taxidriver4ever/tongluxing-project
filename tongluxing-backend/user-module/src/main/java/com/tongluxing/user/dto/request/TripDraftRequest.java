package com.tongluxing.user.dto.request;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 行程草稿创建/修改请求。
 *
 * <p>起点和终点必须完整有效，途经点可以为空；人数、天数在模型层限制合理范围，
 * 出发时间是否满足业务提前量由对应行程 Service 判断。</p>
 *
 * @param startLocation 行程起点
 * @param endLocation 行程终点
 * @param waypoints 可选途经点列表
 * @param departureTime 计划出发时间
 * @param durationDays 预计持续天数
 * @param peopleCount 计划出行人数
 * @param remark 草稿备注
 */
public record TripDraftRequest(
        @NotNull @Valid LocationRequest startLocation,
        @NotNull @Valid LocationRequest endLocation,
        @Valid List<LocationRequest> waypoints,
        @NotNull LocalDateTime departureTime,
        @NotNull @Min(1) @Max(365) Integer durationDays,
        @NotNull @Min(1) @Max(100) Integer peopleCount,
        @Size(max = 255) String remark
) {
}

