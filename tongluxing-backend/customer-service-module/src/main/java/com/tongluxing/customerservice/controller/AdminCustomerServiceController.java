package com.tongluxing.customerservice.controller;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tongluxing.common.result.Result;
import com.tongluxing.customerservice.dto.AssignTicketRequest;
import com.tongluxing.customerservice.dto.CloseTicketRequest;
import com.tongluxing.customerservice.dto.ReplyTicketRequest;
import com.tongluxing.customerservice.service.CustomerServiceTicketService;
import com.tongluxing.customerservice.vo.PageResult;
import com.tongluxing.customerservice.vo.TicketVO;

import lombok.RequiredArgsConstructor;

/**
 * 运营侧客服工单接口。
 *
 * <p>运营接口负责工单池查询、分配、回复和关闭。当前模块以 operatorId 记录操作人，
 * 后续接入后台账号权限体系时，可以在该层或安全拦截器中替换为真实后台登录身份。</p>
 */
@RestController
@RequiredArgsConstructor
public class AdminCustomerServiceController {

    private final CustomerServiceTicketService ticketService;

    /**
     * 查询运营工单池，可按 OPEN、PROCESSING、CLOSED 等状态过滤。
     */
    @GetMapping("/v1/admin/customer-service/tickets")
    public Result<PageResult<TicketVO>> tickets(@RequestParam(required = false) String status,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return Result.success(ticketService.listAdminTickets(status, page, size));
    }

    /**
     * 将工单分配给指定运营人员，并把工单推进到处理中。
     */
    @PostMapping("/v1/admin/customer-service/tickets/{ticketId}/assign")
    public Result<TicketVO> assign(@PathVariable Long ticketId,
                                   @Valid @RequestBody AssignTicketRequest request) {
        return Result.success(ticketService.assign(ticketId, request));
    }

    /**
     * 运营回复工单；首次回复也会自动把 OPEN 工单推进为 PROCESSING。
     */
    @PostMapping("/v1/admin/customer-service/tickets/{ticketId}/reply")
    public Result<TicketVO> reply(@PathVariable Long ticketId,
                                  @Valid @RequestBody ReplyTicketRequest request) {
        return Result.success(ticketService.reply(ticketId, request));
    }

    /**
     * 关闭工单，关闭后会记录关闭时间，后续不可再次回复或分配。
     */
    @PostMapping("/v1/admin/customer-service/tickets/{ticketId}/close")
    public Result<TicketVO> close(@PathVariable Long ticketId,
                                  @Valid @RequestBody CloseTicketRequest request) {
        return Result.success(ticketService.close(ticketId, request));
    }
}
