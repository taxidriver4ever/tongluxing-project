package com.tongluxing.customerservice.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.customerservice.dto.CustomerServiceQueryDTO;

/**
 * 客服工单数据库访问接口。
 *
 * <p>该 Mapper 负责维护客服工单和工单消息两张事实表。工单表保存状态、归属和处理人，
 * 消息表保存用户、运营、系统三类发送方的时间线。</p>
 */
@Mapper
public interface CustomerServiceTicketMapper {

    /**
     * 按幂等请求号查询已创建工单，用于重复提交时返回原结果。
     */
    @Select("""
            select id,
                   creator_type creatorType,
                   creator_id creatorId,
                   scene,
                   target_type targetType,
                   target_id targetId,
                   title,
                   content,
                   ticket_status ticketStatus,
                   priority,
                   assigned_admin_id assignedAdminId,
                   created_at createdAt,
                   updated_at updatedAt,
                   closed_at closedAt
            from customer_service_ticket
            where request_id = #{requestId}
              and deleted = 0
            limit 1
            """)
    CustomerServiceQueryDTO findTicketByRequestId(@Param("requestId") String requestId);

    @Select("select count(*) from customer_service_ticket_message where request_id=#{requestId} and deleted=0")
    int countMessageByRequestId(@Param("requestId") String requestId);

    /**
     * 按工单 ID 查询有效工单。
     */
    @Select("""
            select id,
                   creator_type creatorType,
                   creator_id creatorId,
                   scene,
                   target_type targetType,
                   target_id targetId,
                   title,
                   content,
                   ticket_status ticketStatus,
                   priority,
                   assigned_admin_id assignedAdminId,
                   created_at createdAt,
                   updated_at updatedAt,
                   closed_at closedAt
            from customer_service_ticket
            where id = #{ticketId}
              and deleted = 0
            limit 1
            """)
    CustomerServiceQueryDTO findTicketById(@Param("ticketId") Long ticketId);

    /**
     * 插入工单主记录，新工单初始状态固定为 OPEN，优先级固定为 NORMAL。
     */
    @Insert("""
            insert into customer_service_ticket(
                id, creator_type, creator_id, scene, target_type, target_id,
                title, content, ticket_status, priority, request_id,
                created_at, updated_at, deleted
            )
            values (
                #{id}, #{creatorType}, #{creatorId}, #{scene}, #{targetType}, #{targetId},
                #{title}, #{content}, 'OPEN', 'NORMAL', #{requestId},
                #{now}, #{now}, 0
            )
            """)
    int insertTicket(@Param("id") Long id,
                     @Param("creatorType") String creatorType,
                     @Param("creatorId") Long creatorId,
                     @Param("scene") String scene,
                     @Param("targetType") String targetType,
                     @Param("targetId") String targetId,
                     @Param("title") String title,
                     @Param("content") String content,
                     @Param("requestId") String requestId,
                     @Param("now") LocalDateTime now);

    /**
     * 插入工单消息，发送方可为 USER、ADMIN 或 SYSTEM。
     */
    @Insert("""
            insert into customer_service_ticket_message(
                id, ticket_id, sender_type, sender_id, message_type,
                content, image_keys_json, request_id, created_at, deleted
            )
            values (
                #{id}, #{ticketId}, #{senderType}, #{senderId}, #{messageType},
                #{content}, #{imageKeysJson}, #{requestId}, #{now}, 0
            )
            """)
    int insertMessage(@Param("id") Long id,
                      @Param("ticketId") Long ticketId,
                      @Param("senderType") String senderType,
                      @Param("senderId") Long senderId,
                      @Param("messageType") String messageType,
                      @Param("content") String content,
                      @Param("imageKeysJson") String imageKeysJson,
                      @Param("requestId") String requestId,
                      @Param("now") LocalDateTime now);

    /**
     * 查询工单消息时间线，按创建时间升序返回。
     */
    @Select("""
            select id,
                   ticket_id targetId,
                   sender_type senderType,
                   sender_id senderId,
                   message_type messageType,
                   content,
                   image_keys_json imageKeysJson,
                   created_at createdAt
            from customer_service_ticket_message
            where ticket_id = #{ticketId}
              and deleted = 0
            order by created_at
            """)
    List<CustomerServiceQueryDTO> findMessages(@Param("ticketId") Long ticketId);

    /**
     * 查询指定用户创建的工单列表。
     */
    @Select("""
            select id,
                   creator_type creatorType,
                   creator_id creatorId,
                   scene,
                   target_type targetType,
                   target_id targetId,
                   title,
                   content,
                   ticket_status ticketStatus,
                   priority,
                   assigned_admin_id assignedAdminId,
                   created_at createdAt,
                   updated_at updatedAt,
                   closed_at closedAt
            from customer_service_ticket
            where creator_type = #{creatorType}
              and creator_id = #{creatorId}
              and deleted = 0
            order by created_at desc
            limit #{offset}, #{size}
            """)
    List<CustomerServiceQueryDTO> listMyTickets(@Param("creatorType") String creatorType,
                                                @Param("creatorId") Long creatorId,
                                                @Param("offset") int offset,
                                                @Param("size") int size);

    /**
     * 统计指定用户创建的工单数量。
     */
    @Select("""
            select count(*)
            from customer_service_ticket
            where creator_type = #{creatorType}
              and creator_id = #{creatorId}
              and deleted = 0
            """)
    long countMyTickets(@Param("creatorType") String creatorType, @Param("creatorId") Long creatorId);

    /**
     * 查询运营工单池，可按状态过滤。
     */
    @Select("""
            <script>
            select id,
                   creator_type creatorType,
                   creator_id creatorId,
                   scene,
                   target_type targetType,
                   target_id targetId,
                   title,
                   content,
                   ticket_status ticketStatus,
                   priority,
                   assigned_admin_id assignedAdminId,
                   created_at createdAt,
                   updated_at updatedAt,
                   closed_at closedAt
            from customer_service_ticket
            where deleted = 0
              <if test="status != null and status != ''">
                and ticket_status = #{status}
              </if>
            order by created_at desc
            limit #{offset}, #{size}
            </script>
            """)
    List<CustomerServiceQueryDTO> listAdminTickets(@Param("status") String status,
                                                   @Param("offset") int offset,
                                                   @Param("size") int size);

    /**
     * 统计运营工单池数量，可按状态过滤。
     */
    @Select("""
            <script>
            select count(*)
            from customer_service_ticket
            where deleted = 0
              <if test="status != null and status != ''">
                and ticket_status = #{status}
              </if>
            </script>
            """)
    long countAdminTickets(@Param("status") String status);

    /**
     * 将工单推进到处理中，并记录当前处理人。
     */
    @Update("""
            update customer_service_ticket
            set ticket_status = 'PROCESSING',
                assigned_admin_id = #{operatorId},
                updated_at = #{now}
            where id = #{ticketId}
              and ticket_status in ('OPEN', 'PROCESSING')
              and deleted = 0
            """)
    int markProcessing(@Param("ticketId") Long ticketId,
                       @Param("operatorId") Long operatorId,
                       @Param("now") LocalDateTime now);

    /**
     * 关闭工单并记录关闭时间，已关闭工单不会再次更新。
     */
    @Update("""
            update customer_service_ticket
            set ticket_status = 'CLOSED',
                assigned_admin_id = #{operatorId},
                updated_at = #{now},
                closed_at = #{now}
            where id = #{ticketId}
              and ticket_status != 'CLOSED'
              and deleted = 0
            """)
    int closeTicket(@Param("ticketId") Long ticketId,
                    @Param("operatorId") Long operatorId,
                    @Param("now") LocalDateTime now);
}
