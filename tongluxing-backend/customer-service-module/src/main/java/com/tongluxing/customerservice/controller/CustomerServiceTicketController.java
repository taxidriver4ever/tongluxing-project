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
 */
@RestController
@RequiredArgsConstructor
public class CustomerServiceTicketController {

    private final CustomerServiceTicketService ticketService;

    @PostMapping("/v1/customer-service/tickets")
    public Result<TicketVO> createTicket(@Valid @RequestBody CreateTicketRequest request) {
        return Result.success(ticketService.createTicket(request));
    }

    /**
     * 投诉快捷入口，底层复用工单事实表，便于未接入第三方客服前先完成闭环。
     */
    @PostMapping("/v1/customer-service/complaints")
    public Result<TicketVO> createComplaint(@Valid @RequestBody CreateTicketRequest request) {
        CreateTicketRequest complaint = new CreateTicketRequest("COMPLAINT", request.targetType(),
                request.targetId(), request.title(), request.content(), request.imageKeys(), request.requestId());
        return Result.success(ticketService.createTicket(complaint));
    }

    @GetMapping("/v1/customer-service/tickets/me")
    public Result<PageResult<TicketVO>> myTickets(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return Result.success(ticketService.listMyTickets(page, size));
    }

    @GetMapping("/v1/customer-service/tickets/{ticketId}")
    public Result<TicketVO> detail(@PathVariable Long ticketId) {
        return Result.success(ticketService.ticketDetail(ticketId));
    }
}
