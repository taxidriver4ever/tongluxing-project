package com.tongluxing.customerservice.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.customerservice.dto.CloseTicketRequest;
import com.tongluxing.customerservice.dto.ReplyTicketRequest;
import com.tongluxing.customerservice.service.CustomerServiceTicketService;
import com.tongluxing.customerservice.vo.PageResult;
import com.tongluxing.customerservice.vo.TicketVO;

import lombok.RequiredArgsConstructor;

/**
 * 运营侧客服工单接口。
 */
@RestController
@RequiredArgsConstructor
public class AdminCustomerServiceController {

    private final CustomerServiceTicketService ticketService;

    @GetMapping("/v1/admin/customer-service/tickets")
    public Result<PageResult<TicketVO>> tickets(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.success(ticketService.listAdminTickets(status, page, size));
    }

    @PostMapping("/v1/admin/customer-service/tickets/{ticketId}/reply")
    public Result<TicketVO> reply(@PathVariable Long ticketId,
                                  @Valid @RequestBody ReplyTicketRequest request) {
        return Result.success(ticketService.reply(ticketId, request));
    }

    @PostMapping("/v1/admin/customer-service/tickets/{ticketId}/close")
    public Result<TicketVO> close(@PathVariable Long ticketId,
                                  @Valid @RequestBody CloseTicketRequest request) {
        return Result.success(ticketService.close(ticketId, request));
    }
}
