package com.tongluxing.groupbuy.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.groupbuy.dto.CreateGroupbuyRequest;
import com.tongluxing.groupbuy.dto.PaidParticipantRequest;
import com.tongluxing.groupbuy.service.GroupbuyService;
import com.tongluxing.groupbuy.vo.GroupbuyActivityVO;
import com.tongluxing.groupbuy.vo.PageResult;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
/**
 * GroupbuyController 接口控制器。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/groupbuys")
public class GroupbuyController {
    private final GroupbuyService groupbuyService;

    @PostMapping
    public Result<GroupbuyActivityVO> create(@Valid @RequestBody CreateGroupbuyRequest request) {
        return Result.success(groupbuyService.create(request));
    }

    @GetMapping
    public Result<PageResult<GroupbuyActivityVO>> list(@RequestParam(required = false) String status,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int size) {
        return Result.success(groupbuyService.list(status, page, size));
    }

    @GetMapping("/{activityId}")
    public Result<GroupbuyActivityVO> detail(@PathVariable Long activityId) {
        return Result.success(groupbuyService.detail(activityId));
    }

    @PostMapping("/{activityId}/paid-participants")
    public Result<GroupbuyActivityVO> paidParticipant(@PathVariable Long activityId,
                                                      @Valid @RequestBody PaidParticipantRequest request) {
        return Result.success(groupbuyService.addPaidParticipant(activityId, request));
    }

    @PostMapping("/{activityId}/expire")
    public Result<GroupbuyActivityVO> expire(@PathVariable Long activityId) {
        return Result.success(groupbuyService.expire(activityId));
    }
}

