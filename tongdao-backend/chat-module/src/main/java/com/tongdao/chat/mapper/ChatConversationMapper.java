package com.tongdao.chat.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongdao.chat.entity.ChatConversation;

/**
 * 聊天会话 Mapper。
 */
@Mapper
public interface ChatConversationMapper {

    /** 根据会话 ID 查询未删除会话。 */
    @Select("""
            select id, biz_type, biz_id, conversation_name, conversation_status, provider_type,
                   provider_conversation_key, last_message_id, last_message_preview, last_message_at,
                   created_at, updated_at, deleted
            from chat_conversation
            where id = #{conversationId} and deleted = 0
            limit 1
            """)
    ChatConversation findById(@Param("conversationId") Long conversationId);

    /** 根据业务类型和业务 ID 查询会话，用于避免重复创建同一业务会话。 */
    @Select("""
            select id, biz_type, biz_id, conversation_name, conversation_status, provider_type,
                   provider_conversation_key, last_message_id, last_message_preview, last_message_at,
                   created_at, updated_at, deleted
            from chat_conversation
            where biz_type = #{bizType} and biz_id = #{bizId} and deleted = 0
            limit 1
            """)
    ChatConversation findByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    /** 查询用户参与的有效会话列表。 */
    @Select("""
            select c.id, c.biz_type, c.biz_id, c.conversation_name, c.conversation_status, c.provider_type,
                   c.provider_conversation_key, c.last_message_id, c.last_message_preview, c.last_message_at,
                   c.created_at, c.updated_at, c.deleted
            from chat_conversation c
            join chat_conversation_member m on m.conversation_id = c.id and m.deleted = 0
            where m.user_id = #{userId} and m.member_status = 'ACTIVE'
              and c.conversation_status = 'ACTIVE' and c.deleted = 0
            order by c.last_message_at desc, c.created_at desc
            """)
    List<ChatConversation> findActiveByUserId(@Param("userId") Long userId);

    /** 新增会话。 */
    @Insert("""
            insert into chat_conversation
                (id, biz_type, biz_id, conversation_name, conversation_status, provider_type,
                 provider_conversation_key, last_message_id, last_message_preview, last_message_at,
                 created_at, updated_at, deleted)
            values
                (#{id}, #{bizType}, #{bizId}, #{conversationName}, #{conversationStatus}, #{providerType},
                 #{providerConversationKey}, #{lastMessageId}, #{lastMessagePreview}, #{lastMessageAt},
                 #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(ChatConversation conversation);

    /** 更新会话最后一条消息摘要。 */
    @Update("""
            update chat_conversation
            set last_message_id = #{messageId},
                last_message_preview = #{preview},
                last_message_at = #{messageAt},
                updated_at = #{messageAt}
            where id = #{conversationId} and deleted = 0
            """)
    void updateLastMessage(@Param("conversationId") Long conversationId,
                           @Param("messageId") Long messageId,
                           @Param("preview") String preview,
                           @Param("messageAt") LocalDateTime messageAt);
}
