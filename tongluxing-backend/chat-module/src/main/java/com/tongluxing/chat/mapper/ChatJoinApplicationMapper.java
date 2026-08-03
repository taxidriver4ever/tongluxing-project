package com.tongluxing.chat.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;
import com.tongluxing.chat.entity.ChatJoinApplication;

/**
 * 聊天加入申请 MyBatis 数据访问接口。
 * 方法直接对应数据库读写语句；事务边界由调用它的服务层统一管理。
 */
@Mapper
public interface ChatJoinApplicationMapper {
    /** 写入一条初始状态为 PENDING 的入群申请。 */
    @Insert("""
        insert into chat_join_application(id,conversation_id,applicant_user_id,application_message,
          application_status,created_at,updated_at,deleted)
        values(#{id},#{conversationId},#{applicantUserId},#{applicationMessage},#{applicationStatus},#{createdAt},#{updatedAt},0)
        """)
    void insert(ChatJoinApplication row);

    /**
     * 查询指定群主有权审核的申请队列。
     * SQL 通过 ACTIVE + OWNER 成员记录完成权限范围约束，而非读取全量数据后再由 Java 过滤。
     */
    @Select("""
        select a.id,a.conversation_id conversationId,a.applicant_user_id applicantUserId,
          a.application_message applicationMessage,a.application_status applicationStatus,
          a.reviewer_user_id reviewerUserId,a.reviewed_at reviewedAt,a.created_at createdAt,a.updated_at updatedAt
        from chat_join_application a
        join chat_conversation_member m on m.conversation_id=a.conversation_id and m.user_id=#{ownerId}
          and m.member_role='OWNER' and m.member_status='ACTIVE' and m.deleted=0
        where a.application_status=#{status} and a.deleted=0 order by a.created_at desc
        """)
    List<ChatJoinApplication> findOwnerQueue(@Param("ownerId") Long ownerId, @Param("status") String status);

    /** 按主键查询未被逻辑删除的申请。 */
    @Select("""
        select id,conversation_id conversationId,applicant_user_id applicantUserId,
          application_message applicationMessage,application_status applicationStatus,
          reviewer_user_id reviewerUserId,reviewed_at reviewedAt,created_at createdAt,updated_at updatedAt
        from chat_join_application where id=#{id} and deleted=0 limit 1
        """)
    ChatJoinApplication findById(@Param("id") Long id);

    /** 查询用户在同一会话中尚未处理的申请，用于提交前防重。 */
    @Select("""
        select id,conversation_id conversationId,applicant_user_id applicantUserId,
          application_message applicationMessage,application_status applicationStatus,
          reviewer_user_id reviewerUserId,reviewed_at reviewedAt,created_at createdAt,updated_at updatedAt
        from chat_join_application
        where conversation_id=#{conversationId} and applicant_user_id=#{applicantUserId}
          and application_status='PENDING' and deleted=0 limit 1
        """)
    ChatJoinApplication findPending(@Param("conversationId") Long conversationId,
                                    @Param("applicantUserId") Long applicantUserId);

    /**
     * 审核申请并记录审核人和时间。
     * 更新条件限定原状态必须为 PENDING，返回值可用于识别并发或重复审核。
     */
    @Update("""
        update chat_join_application set application_status=#{decision},reviewer_user_id=#{reviewerId},
          reviewed_at=#{now},updated_at=#{now}
        where id=#{id} and application_status='PENDING' and deleted=0
        """)
    int review(@Param("id") Long id, @Param("decision") String decision,
               @Param("reviewerId") Long reviewerId, @Param("now") LocalDateTime now);
}
