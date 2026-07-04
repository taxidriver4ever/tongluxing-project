package com.tongluxing.notify.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.notify.dto.NotifyMessageQueryDTO;

/**
 * 通知消息数据库访问接口。
 */
@Mapper
public interface NotifyMessageMapper {

    /**
     * 根据幂等号查询通知。
     */
    @Select("""
            select id,
                   receiver_type receiverType,
                   receiver_id receiverId,
                   scene,
                   event_type eventType,
                   title,
                   content,
                   target_type targetType,
                   target_id targetId,
                   read_status readStatus,
                   read_at readAt,
                   created_at createdAt
            from notify_message
            where request_id = #{requestId}
              and deleted = 0
            limit 1
            """)
    NotifyMessageQueryDTO findByRequestId(@Param("requestId") String requestId);

    /**
     * 写入通知消息。
     */
    @Insert("""
            insert into notify_message(
                id, receiver_type, receiver_id, scene, event_type,
                title, content, target_type, target_id, read_status,
                request_id, created_at, updated_at, deleted
            )
            values (
                #{id}, #{receiverType}, #{receiverId}, #{scene}, #{eventType},
                #{title}, #{content}, #{targetType}, #{targetId}, 'UNREAD',
                #{requestId}, #{now}, #{now}, 0
            )
            """)
    int insertMessage(@Param("id") Long id,
                      @Param("receiverType") String receiverType,
                      @Param("receiverId") Long receiverId,
                      @Param("scene") String scene,
                      @Param("eventType") String eventType,
                      @Param("title") String title,
                      @Param("content") String content,
                      @Param("targetType") String targetType,
                      @Param("targetId") String targetId,
                      @Param("requestId") String requestId,
                      @Param("now") LocalDateTime now);

    /**
     * 分页查询通知。
     */
    @Select("""
            <script>
            select id,
                   receiver_type receiverType,
                   receiver_id receiverId,
                   scene,
                   event_type eventType,
                   title,
                   content,
                   target_type targetType,
                   target_id targetId,
                   read_status readStatus,
                   read_at readAt,
                   created_at createdAt
            from notify_message
            where receiver_type = #{receiverType}
              and receiver_id = #{receiverId}
              and deleted = 0
              <if test="scene != null and scene != ''">
                and scene = #{scene}
              </if>
              <if test="unreadOnly">
                and read_status = 'UNREAD'
              </if>
            order by created_at desc
            limit #{offset}, #{size}
            </script>
            """)
    List<NotifyMessageQueryDTO> listMessages(@Param("receiverType") String receiverType,
                                             @Param("receiverId") Long receiverId,
                                             @Param("scene") String scene,
                                             @Param("unreadOnly") boolean unreadOnly,
                                             @Param("offset") int offset,
                                             @Param("size") int size);

    /**
     * 统计通知数量。
     */
    @Select("""
            <script>
            select count(*)
            from notify_message
            where receiver_type = #{receiverType}
              and receiver_id = #{receiverId}
              and deleted = 0
              <if test="scene != null and scene != ''">
                and scene = #{scene}
              </if>
              <if test="unreadOnly">
                and read_status = 'UNREAD'
              </if>
            </script>
            """)
    long countMessages(@Param("receiverType") String receiverType,
                       @Param("receiverId") Long receiverId,
                       @Param("scene") String scene,
                       @Param("unreadOnly") boolean unreadOnly);

    /**
     * 统计未读数量。
     */
    @Select("""
            select count(*)
            from notify_message
            where receiver_type = #{receiverType}
              and receiver_id = #{receiverId}
              and read_status = 'UNREAD'
              and deleted = 0
            """)
    long countUnread(@Param("receiverType") String receiverType, @Param("receiverId") Long receiverId);

    /**
     * 标记单条通知已读。
     */
    @Update("""
            update notify_message
            set read_status = 'READ',
                read_at = #{now},
                updated_at = #{now}
            where id = #{id}
              and receiver_type = #{receiverType}
              and receiver_id = #{receiverId}
              and deleted = 0
            """)
    int markRead(@Param("id") Long id,
                 @Param("receiverType") String receiverType,
                 @Param("receiverId") Long receiverId,
                 @Param("now") LocalDateTime now);

    /**
     * 标记接收方全部通知已读。
     */
    @Update("""
            update notify_message
            set read_status = 'READ',
                read_at = #{now},
                updated_at = #{now}
            where receiver_type = #{receiverType}
              and receiver_id = #{receiverId}
              and read_status = 'UNREAD'
              and deleted = 0
            """)
    int markAllRead(@Param("receiverType") String receiverType,
                    @Param("receiverId") Long receiverId,
                    @Param("now") LocalDateTime now);
}
