package com.tongluxing.customerservice.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.customerservice.dto.CreateTicketRequest;
import com.tongluxing.customerservice.service.CustomerServiceTicketService;
import com.tongluxing.customerservice.vo.PageResult;
import com.tongluxing.customerservice.vo.TicketVO;

import lombok.RequiredArgsConstructor;

/**
 * 用户侧客服工单接口。
 *
 * <p>用户只能创建工单、查看自己的工单列表和详情。权限判断由 Service 通过当前登录用户完成，
 * Controller 保持薄层，避免接口层散落工单状态和归属判断。</p>
 */
@RestController
@RequiredArgsConstructor
public class CustomerServiceTicketController {

    private final CustomerServiceTicketService ticketService;

    /**
     * 创建普通客服工单。
     */
    @PostMapping("/v1/customer-service/tickets")
    public Result<TicketVO> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        return Result.success(ticketService.createTicket(request));
    }

    /**
     * 投诉快捷入口。
     *
     * <p>投诉底层仍复用工单事实表，只是强制将 scene 设置为 COMPLAINT，便于运营后台统一处理。</p>
     */
    @PostMapping("/v1/customer-service/complaints")
    public Result<TicketVO> createComplaint(@Valid @RequestBody CreateTicketRequest request) {
        CreateTicketRequest complaint = new CreateTicketRequest("COMPLAINT", request.targetType(),
                request.targetId(), request.title(), request.content(), request.imageKeys(), request.requestId());
        return Result.success(ticketService.createTicket(complaint));
    }

    /**
     * 查询当前登录用户创建的客服工单。
     */
    @GetMapping("/v1/customer-service/tickets/me")
    public Result<PageResult<TicketVO>> myTickets(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.success(ticketService.listMyTickets(page, size));
    }

    /**
     * 查询工单详情，包含用户消息、系统自动回复和运营回复。
     */
    @GetMapping("/v1/customer-service/tickets/{ticketId}")
    public Result<TicketVO> detail(@PathVariable Long ticketId) {
        return Result.success(ticketService.ticketDetail(ticketId));
    }
}
