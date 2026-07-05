package com.tongluxing.admin.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.service.AdminCompensationTaskService;
import com.tongluxing.common.result.Result;

import lombok.RequiredArgsConstructor;

/**
 * 运营后台补偿任务处理接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/compensation-tasks")
public class AdminCompensationTaskController {

    private final AdminCompensationTaskService taskService;

    /**
     * 手动触发到期补偿任务消费，便于内测阶段观察后台干预是否真实生效。
     */
    @PostMapping("/process")
    public Result<Integer> process(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(taskService.processDueTasks(limit));
    }
}
