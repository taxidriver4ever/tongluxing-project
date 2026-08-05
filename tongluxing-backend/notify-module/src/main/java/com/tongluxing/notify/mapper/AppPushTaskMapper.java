package com.tongluxing.notify.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.notify.entity.AppPushTask;

/** 系统推送任务 Mapper，提供幂等入队和失败重试。 */
@Mapper
public interface AppPushTaskMapper {

    @Insert("""
            insert ignore into app_push_task
                (id, user_id, event_type, title, content, payload_json, idempotency_key,
                 delivery_status, retry_count, next_retry_at, last_error, created_at, updated_at, deleted)
            values
                (#{id}, #{userId}, #{eventType}, #{title}, #{content}, #{payloadJson}, #{idempotencyKey},
                 'PENDING', 0, #{nextRetryAt}, '', #{createdAt}, #{updatedAt}, 0)
            """)
    int insertIgnore(AppPushTask task);

    @Select("""
            select id, user_id, event_type, title, content, payload_json, idempotency_key,
                   delivery_status, retry_count, next_retry_at, last_error, created_at, updated_at
            from app_push_task
            where deleted = 0 and delivery_status in ('PENDING', 'FAILED')
              and (next_retry_at is null or next_retry_at <= #{now})
              and retry_count < 6
            order by created_at asc
            limit #{limit}
            """)
    List<AppPushTask> findDue(@Param("now") LocalDateTime now, @Param("limit") Integer limit);

    @Update("""
            update app_push_task
            set delivery_status = 'SENT', last_error = '', updated_at = #{now}
            where id = #{taskId} and deleted = 0
            """)
    int markSent(@Param("taskId") Long taskId, @Param("now") LocalDateTime now);

    @Update("""
            update app_push_task
            set delivery_status = #{status}, retry_count = retry_count + 1,
                next_retry_at = #{nextRetryAt}, last_error = #{error}, updated_at = #{now}
            where id = #{taskId} and deleted = 0
            """)
    int markFailed(@Param("taskId") Long taskId, @Param("status") String status,
                   @Param("nextRetryAt") LocalDateTime nextRetryAt,
                   @Param("error") String error, @Param("now") LocalDateTime now);
}
