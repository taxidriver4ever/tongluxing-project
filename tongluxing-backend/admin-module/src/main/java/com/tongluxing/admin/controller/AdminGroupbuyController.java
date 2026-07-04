package com.tongluxing.admin.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.dto.AdminInterventionRequest;
import com.tongluxing.admin.service.AdminInterventionService;
import com.tongluxing.admin.service.AdminQueryService;
import com.tongluxing.admin.vo.AdminInterventionResultVO;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.common.result.Result;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 运营后台拼团管理接口。
 *
 * <p>当前支持拼团列表占位查询和拼团人工干预动作记录。</p>
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/groupbuys")
public class AdminGroupbuyController {

    /** 后台人工干预服务。 */
    private final AdminInterventionService interventionService;
    /** 后台通用查询服务。 */
    private final AdminQueryService queryService;

    /** 分页查询拼团活动；当前实现为占位空分页，后续可接入 groupbuy-module 查询端口。 */
    @GetMapping
    public Result<PageResult<Object>> page(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(queryService.emptyBusinessPage("groupbuy-module", status, null, null, null, page, size));
    }

    /** 对指定拼团活动执行人工干预，实际处理通过补偿任务交给业务模块承接。 */
    @PostMapping("/{activityId}/intervene")
    public Result<AdminInterventionResultVO> intervene(@PathVariable Long activityId,
                                                       @Valid @RequestBody AdminInterventionRequest request) {
        return Result.success(interventionService.interveneGroupbuy(activityId, request));
    }
}
