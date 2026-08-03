package com.tongluxing.chat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tongluxing.chat.entity.ChatMessage;

/**
 * 聊天消息 Mapper。
 */
@Mapper
public interface ChatMessageMapper {

    /** 查询会话历史消息；beforeMessageId 为空时查询最新消息。 */
    List<ChatMessage> findMessages(@Param("conversationId") Long conversationId,
                                   @Param("beforeMessageId") Long beforeMessageId,
                                   @Param("clearedBeforeMessageId") Long clearedBeforeMessageId,
                                   @Param("limit") Integer limit);

    /** 查询会话内未被拦截的最新消息 ID，用作已读和隐藏游标。 */
    @org.apache.ibatis.annotations.Select("""
            select max(id) from chat_message
            where conversation_id=#{conversationId} and deleted=0 and message_status<>'BLOCKED'
            """)
    Long findLatestMessageId(@Param("conversationId") Long conversationId);

    /** 统计指定用户在会话中已成功保存且未被拦截的消息数，用于私聊额度计算。 */
    @org.apache.ibatis.annotations.Select("""
            select count(*) from chat_message
            where conversation_id=#{conversationId} and sender_user_id=#{senderUserId}
              and deleted=0 and message_status<>'BLOCKED'
            """)
    int countSentByUser(@Param("conversationId") Long conversationId,
                        @Param("senderUserId") Long senderUserId);

    /** 新增聊天消息。 */
    @Insert("""
            insert into chat_message
                (id, conversation_id, sender_user_id, message_type, message_payload_json,
                 message_status, provider_message_key, sent_at, created_at, updated_at, deleted)
            values
                (#{id}, #{conversationId}, #{senderUserId}, #{messageType}, #{messagePayloadJson},
                 #{messageStatus}, #{providerMessageKey}, #{sentAt}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(ChatMessage message);
}
