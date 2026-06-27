package com.tongdao.admin.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

import com.tongdao.admin.entity.AdminCompensationTask;

/**
 * 后台补偿任务 Mapper。
 */
@Mapper
public interface AdminCompensationTaskMapper {

    /** 插入一条待处理补偿任务。 */
    @Insert("""
            insert into admin_compensation_task
                (id, biz_type, biz_id, idempotent_key, target_module,
                 request_payload, task_status, retry_count, next_retry_at,
                 last_error, created_at, updated_at)
            values
                (#{id}, #{bizType}, #{bizId}, #{idempotentKey}, #{targetModule},
                 #{requestPayload}, #{taskStatus}, #{retryCount}, #{nextRetryAt},
                 #{lastError}, #{createdAt}, #{updatedAt})
            """)
    void insert(AdminCompensationTask task);
}
