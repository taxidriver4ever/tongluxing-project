package com.tongluxing.customerservice.service;

import com.tongluxing.customerservice.dto.AssignTicketRequest;
import com.tongluxing.customerservice.dto.CloseTicketRequest;
import com.tongluxing.customerservice.dto.CreateTicketRequest;
import com.tongluxing.customerservice.dto.ReplyTicketRequest;
import com.tongluxing.customerservice.vo.PageResult;
import com.tongluxing.customerservice.vo.TicketVO;

/**
 * 客服工单业务服务。
 */
public interface CustomerServiceTicketService {

    TicketVO createTicket(CreateTicketRequest request);

    PageResult<TicketVO> listMyTickets(int page, int size);

    TicketVO ticketDetail(Long ticketId);

    PageResult<TicketVO> listAdminTickets(String status, int page, int size);

    TicketVO reply(Long ticketId, ReplyTicketRequest request);

    TicketVO assign(Long ticketId, AssignTicketRequest request);

    TicketVO close(Long ticketId, CloseTicketRequest request);
}
