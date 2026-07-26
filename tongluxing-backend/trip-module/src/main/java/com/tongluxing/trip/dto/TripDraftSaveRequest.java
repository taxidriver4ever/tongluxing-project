package com.tongluxing.trip.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** 草稿基础信息增量保存请求；所有字段均允许未完成。 */
public record TripDraftSaveRequest(
        @Size(max = 128) String title,
        String startTime,
        @Valid LocationRequest startLocation,
        @Valid LocationRequest destination,
        @Size(max = 1000) String description,
        @Size(max = 512) String coverImageKey,
        @Min(1) @Max(20) Integer expectPeople,
        @Min(1) @Max(365) Integer durationDays,
        @Size(max = 8) List<@Size(max = 16) String> vehicleRequirements,
        @Size(max = 128) String budgetDescription,
        @Size(max = 255) String notes
) { }
