package com.tongluxing.trip.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/** 经停点排序请求，数组顺序即新的路线顺序。 */
public record TripWaypointOrderRequest(@NotEmpty @Size(max = 5) List<Long> waypointIds) { }
