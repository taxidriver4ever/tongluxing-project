package com.tongluxing.order.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.order.entity.OrderCompensationTask;
/**
 * 订单补偿任务 Mapper。
 */
@Mapper
public interface OrderCompensationTaskMapper {

    /**
     * 新增一条待处理补偿任务。
     */
    @Insert("""
            insert into order_compensation_task
                (id, biz_type, biz_id, idempotent_key, target_module, request_payload,
                 task_status, retry_count, next_retry_at, last_error, created_at, updated_at)
            values
                (#{id}, #{bizType}, #{bizId}, #{idempotentKey}, #{targetModule}, #{requestPayload},
                 #{taskStatus}, #{retryCount}, #{nextRetryAt}, #{lastError}, #{createdAt}, #{updatedAt})
            """)
    void insert(OrderCompensationTask task);

    /**
     * 查询到期补偿任务，供定时任务或内部接口消费。
     */
    @Select("""
            select id, biz_type, biz_id, idempotent_key, target_module,
                   request_payload, task_status, retry_count, next_retry_at,
                   last_error, created_at, updated_at
            from order_compensation_task
            where task_status in ('PENDING', 'FAILED')
              and (next_retry_at is null or next_retry_at <= #{now})
            order by created_at asc
            limit #{limit}
            """)
    List<OrderCompensationTask> listDueTasks(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /**
     * 标记补偿任务成功。
     */
    @Update("""
            update order_compensation_task
            set task_status = 'SUCCESS',
                last_error = null,
                updated_at = #{now}
            where id = #{taskId}
            """)
    int markSuccess(@Param("taskId") Long taskId, @Param("now") LocalDateTime now);

    /**
     * 标记补偿任务失败并推迟下次重试时间。
     */
    @Update("""
            update order_compensation_task
            set task_status = 'FAILED',
                retry_count = retry_count + 1,
                next_retry_at = #{nextRetryAt},
                last_error = #{error},
                updated_at = #{now}
            where id = #{taskId}
            """)
    int markFailed(@Param("taskId") Long taskId, @Param("error") String error,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt, @Param("now") LocalDateTime now);
}
