package com.tongluxing.admin.service;

/**
 * 后台补偿任务消费服务。
 */
public interface AdminCompensationTaskService {

    int processDueTasks(int limit);
}
