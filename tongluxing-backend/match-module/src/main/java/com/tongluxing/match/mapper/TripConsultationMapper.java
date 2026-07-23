package com.tongluxing.match.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TripConsultationMapper {

    @Select("""
            SELECT COUNT(1) FROM trip_consultation_request
            WHERE trip_id=#{tripId} AND sender_user_id=#{senderUserId} AND request_status='PENDING'
            """)
    int pendingExists(@Param("tripId") Long tripId, @Param("senderUserId") Long senderUserId);

    @Insert("""
            INSERT INTO trip_consultation_request(
              id,trip_id,trip_title,sender_user_id,receiver_user_id,content,request_status,created_at,updated_at
            ) VALUES(#{id},#{tripId},#{tripTitle},#{senderUserId},#{receiverUserId},#{content},'PENDING',NOW(),NOW())
            """)
    int insert(@Param("id") Long id, @Param("tripId") Long tripId, @Param("tripTitle") String tripTitle,
            @Param("senderUserId") Long senderUserId, @Param("receiverUserId") Long receiverUserId,
            @Param("content") String content);
}
