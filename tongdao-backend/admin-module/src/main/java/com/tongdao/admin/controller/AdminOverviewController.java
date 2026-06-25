package com.tongdao.admin.controller;

import java.time.LocalDateTime;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongdao.admin.service.AdminOverviewService;
import com.tongdao.admin.vo.AdminOperationOverviewVO;
import com.tongdao.common.result.Result;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin")
public class AdminOverviewController {
    private final AdminOverviewService overviewService;

    @GetMapping("/operation-overview")
    public Result<AdminOperationOverviewVO> overview(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime endTime) {
        return Result.success(overviewService.overview(startTime, endTime));
    }
}
