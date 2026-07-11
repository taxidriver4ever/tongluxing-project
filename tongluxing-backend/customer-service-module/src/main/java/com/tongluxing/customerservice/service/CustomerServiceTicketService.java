package com.tongluxing.customerservice.service;

import com.tongluxing.customerservice.dto.AssignTicketRequest;
import com.tongluxing.customerservice.dto.CloseTicketRequest;
import com.tongluxing.customerservice.dto.CreateTicketRequest;
import com.tongluxing.customerservice.dto.ReplyTicketRequest;
import com.tongluxing.customerservice.vo.PageResult;
import com.tongluxing.customerservice.vo.TicketVO;

/**
 * 客服工单业务服务。
 *
 * <p>该接口封装客服工单的完整本地闭环：创建、查询、分配、回复和关闭。
 * Controller、运营后台和后续第三方回调都应复用这里的状态流转规则。</p>
 */
public interface CustomerServiceTicketService {

    /**
     * 创建用户客服工单，并写入首条用户消息和系统自动回复。
     *
     * @param request 创建工单请求
     * @return 创建后的工单详情
     */
    TicketVO createTicket(CreateTicketRequest request);

    /**
     * 分页查询当前登录用户创建的工单。
     *
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 当前用户工单分页结果
     */
    PageResult<TicketVO> listMyTickets(int page, int size);

    /**
     * 查询工单详情；用户侧会校验工单归属。
     *
     * @param ticketId 工单 ID
     * @return 工单详情和消息列表
     */
    TicketVO ticketDetail(Long ticketId);

    /**
     * 运营分页查询工单池。
     *
     * @param status 工单状态，可为空
     * @param page 页码，从 1 开始
     * @param size 每页数量
     * @return 工单池分页结果
     */
    PageResult<TicketVO> listAdminTickets(String status, int page, int size);

    /**
     * 运营回复工单。
     *
     * @param ticketId 工单 ID
     * @param request 回复内容和操作人
     * @return 回复后的工单详情
     */
    TicketVO reply(Long ticketId, ReplyTicketRequest request);

    /**
     * 分配工单给运营人员。
     *
     * @param ticketId 工单 ID
     * @param request 分配请求
     * @return 分配后的工单详情
     */
    TicketVO assign(Long ticketId, AssignTicketRequest request);

    /**
     * 关闭工单。
     *
     * @param ticketId 工单 ID
     * @param request 关闭请求
     * @return 关闭后的工单详情
     */
    TicketVO close(Long ticketId, CloseTicketRequest request);
}
