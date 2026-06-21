package com.tongdao.chat.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.chat.entity.ChatConversationMember;

@Mapper
public interface ChatConversationMemberMapper {

    @Select("""
            select id, conversation_id, user_id, member_role, member_status, unread_count,
                   last_read_message_id, joined_at, exited_at, created_at, updated_at, deleted
            from chat_conversation_member
            where conversation_id = #{conversationId} and user_id = #{userId} and deleted = 0
            limit 1
            """)
    ChatConversationMember findByConversationAndUser(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Select("""
            select id, conversation_id, user_id, member_role, member_status, unread_count,
                   last_read_message_id, joined_at, exited_at, created_at, updated_at, deleted
            from chat_conversation_member
            where conversation_id = #{conversationId} and member_status = 'ACTIVE' and deleted = 0
            """)
    List<ChatConversationMember> findActiveByConversationId(@Param("conversationId") Long conversationId);

    @Insert("""
            insert into chat_conversation_member
                (id, conversation_id, user_id, member_role, member_status, unread_count,
                 last_read_message_id, joined_at, exited_at, created_at, updated_at, deleted)
            values
                (#{id}, #{conversationId}, #{userId}, #{memberRole}, #{memberStatus}, #{unreadCount},
                 #{lastReadMessageId}, #{joinedAt}, #{exitedAt}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(ChatConversationMember member);

    @Update("""
            update chat_conversation_member
            set member_status = 'EXITED', exited_at = #{now}, updated_at = #{now}
            where conversation_id = #{conversationId} and user_id = #{userId}
              and member_status = 'ACTIVE' and deleted = 0
            """)
    int exit(@Param("conversationId") Long conversationId, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Update("""
            update chat_conversation_member
            set unread_count = unread_count + 1, updated_at = #{now}
            where conversation_id = #{conversationId} and user_id <> #{senderUserId}
              and member_status = 'ACTIVE' and deleted = 0
            """)
    void incrementUnread(@Param("conversationId") Long conversationId, @Param("senderUserId") Long senderUserId, @Param("now") LocalDateTime now);

    @Update("""
            update chat_conversation_member
            set member_role = #{role},
                member_status = 'ACTIVE',
                unread_count = 0,
                joined_at = #{now},
                exited_at = null,
                updated_at = #{now}
            where conversation_id = #{conversationId} and user_id = #{userId} and deleted = 0
            """)
    int reactivate(@Param("conversationId") Long conversationId,
                   @Param("userId") Long userId,
                   @Param("role") String role,
                   @Param("now") LocalDateTime now);
}
