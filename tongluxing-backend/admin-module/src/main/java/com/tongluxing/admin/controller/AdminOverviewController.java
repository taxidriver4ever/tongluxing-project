package com.tongluxing.admin.controller;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.service.AdminOverviewService;
import com.tongluxing.admin.vo.AdminOperationOverviewVO;
import com.tongluxing.common.result.Result;

import lombok.RequiredArgsConstructor;

/**
 * 运营后台总览接口。
 *
 * <p>提供运营数据概览查询入口，当前由 {@link AdminOverviewService} 负责汇总和缓存。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin")
public class AdminOverviewController {

    /** 运营概览服务。 */
    private final AdminOverviewService overviewService;

    /** 查询指定时间范围内的运营概览数据；时间为空时表示全量范围。 */
    @GetMapping("/operation-overview")
    public Result<AdminOperationOverviewVO> overview(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(overviewService.overview(startTime, endTime));
    }
}
