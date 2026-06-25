package com.tongdao.admin.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.admin.dto.AdminInterventionRequest;
import com.tongdao.admin.service.AdminInterventionService;
import com.tongdao.admin.service.AdminQueryService;
import com.tongdao.admin.vo.AdminInterventionResultVO;
import com.tongdao.admin.vo.PageResult;
import com.tongdao.common.result.Result;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/groupbuys")
public class AdminGroupbuyController {
    private final AdminInterventionService interventionService;
    private final AdminQueryService queryService;

    @GetMapping
    public Result<PageResult<Object>> page(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(queryService.emptyBusinessPage("groupbuy-module", status, null, null, null, page, size));
    }

    @PostMapping("/{activityId}/intervene")
    public Result<AdminInterventionResultVO> intervene(@PathVariable Long activityId,
                                                       @Valid @RequestBody AdminInterventionRequest request) {
        return Result.success(interventionService.interveneGroupbuy(activityId, request));
    }
}
