package com.tongluxing.order.service;

/**
 * 订单补偿任务消费服务。
 */
public interface OrderCompensationTaskService {

    /**
     * 消费到期订单补偿任务。
     *
     * @param limit 单批最大处理数量
     * @return 成功处理的任务数量
     */
    int processDueTasks(int limit);
}
