package com.tongluxing.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.service.AdminQueryService;
import com.tongluxing.admin.vo.PageResult;
import com.tongluxing.common.result.Result;

import lombok.RequiredArgsConstructor;

/**
 * 运营后台订单查询接口。
 *
 * <p>当前订单列表为占位空分页，后续可通过 order-module 查询端口补齐真实数据。</p>
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/admin/orders")
public class AdminOrderController {

    /** 后台通用查询服务。 */
    private final AdminQueryService queryService;

    /** 分页查询订单列表；当前返回空分页。 */
    @GetMapping
    public Result<PageResult<Object>> page(@RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(queryService.emptyBusinessPage("order-module", status, null, null, null, page, size));
    }
}
