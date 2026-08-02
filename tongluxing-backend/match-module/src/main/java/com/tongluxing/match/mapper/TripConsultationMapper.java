package com.tongluxing.match.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 行程咨询 MyBatis 数据访问接口。
 * 方法直接对应数据库读写语句；事务边界由调用它的服务层统一管理。
 */
@Mapper
public interface TripConsultationMapper {

    /**
     * 查询同一发送者针对同一行程的待处理咨询数量。
     *
     * @param tripId 目标行程 ID
     * @param senderUserId 咨询发送者 ID
     * @return 待处理咨询数，用于阻止重复发送
     */
    @Select("""
            SELECT COUNT(1) FROM trip_consultation_request
            WHERE trip_id=#{tripId} AND sender_user_id=#{senderUserId} AND request_status='PENDING'
            """)
    int pendingExists(@Param("tripId") Long tripId, @Param("senderUserId") Long senderUserId);

    /**
     * 新建待处理咨询。
     *
     * <p>行程标题冗余保存，避免行程后续改名影响咨询列表的历史上下文。</p>
     *
     * @param id 咨询主键
     * @param tripId 行程 ID
     * @param tripTitle 行程标题快照
     * @param senderUserId 发送者 ID
     * @param receiverUserId 行程发起人 ID
     * @param content 咨询正文
     * @return 成功插入的行数
     */
    @Insert("""
            INSERT INTO trip_consultation_request(
              id,trip_id,trip_title,sender_user_id,receiver_user_id,content,request_status,created_at,updated_at
            ) VALUES(#{id},#{tripId},#{tripTitle},#{senderUserId},#{receiverUserId},#{content},'PENDING',NOW(),NOW())
            """)
    int insert(@Param("id") Long id, @Param("tripId") Long tripId, @Param("tripTitle") String tripTitle,
            @Param("senderUserId") Long senderUserId, @Param("receiverUserId") Long receiverUserId,
            @Param("content") String content);
}
