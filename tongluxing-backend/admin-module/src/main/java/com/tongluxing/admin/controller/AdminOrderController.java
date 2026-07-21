package com.tongluxing.admin.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.admin.service.AdminTradeService;
import com.tongluxing.admin.vo.AdminOrderVO;
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
    private final AdminTradeService tradeService;

    /** 分页查询订单列表；当前返回空分页。 */
    @GetMapping
    public Result<PageResult<AdminOrderVO>> page(@RequestParam(required = false) String status,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") LocalDateTime startTime,
                                           @RequestParam(required = false) @DateTimeFormat(pattern="yyyy-MM-dd HH:mm:ss") LocalDateTime endTime,
                                           @RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return Result.success(tradeService.orders(status,keyword,startTime,endTime,page,size));
    }

    @GetMapping("/{orderId}") public Result<AdminOrderVO> detail(@PathVariable Long orderId){return Result.success(tradeService.order(orderId));}
}
