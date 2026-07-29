package com.tongluxing.chat.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.chat.entity.ChatConversation;

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

    /** 按稳定的服务商会话 Key 查询私聊，避免重复创建同一对用户的会话。 */
    @Select("""
            select id, biz_type, biz_id, conversation_name, conversation_status, provider_type,
                   provider_conversation_key, last_message_id, last_message_preview, last_message_at,
                   created_at, updated_at, deleted
            from chat_conversation
            where biz_type = #{bizType} and provider_conversation_key = #{providerKey} and deleted = 0
            limit 1
            """)
    ChatConversation findByProviderKey(@Param("bizType") String bizType,
                                       @Param("providerKey") String providerKey);

    /** 查询用户参与的有效会话列表。 */
    @Select("""
            <script>
            select c.id, c.biz_type, c.biz_id, c.conversation_name, c.conversation_status, c.provider_type,
                   c.provider_conversation_key, c.last_message_id, c.last_message_preview, c.last_message_at,
                   c.created_at, c.updated_at, c.deleted
            from chat_conversation c
            join chat_conversation_member m on m.conversation_id = c.id and m.deleted = 0
            where m.user_id = #{userId} and m.member_status = 'ACTIVE'
              and c.conversation_status in ('ACTIVE','HISTORY') and c.deleted = 0
              and (m.cleared_before_message_id is null
                   or coalesce(c.last_message_id, 0) &gt; m.cleared_before_message_id)
            <if test="title != null and title != ''">
              and lower(c.conversation_name) like concat('%', lower(#{title}), '%')
            </if>
            order by m.pinned_flag desc, c.last_message_at desc, c.created_at desc
            </script>
            """)
    List<ChatConversation> findActiveByUserId(@Param("userId") Long userId,
                                              @Param("title") String title);

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

    /** 更新聊天供应商，用于将历史 MOCK/LOCAL 会话惰性迁移到腾讯 IM。 */
    @Update("""
            update chat_conversation
            set provider_type = #{providerType},
                provider_conversation_key = #{providerKey},
                updated_at = #{now}
            where id = #{conversationId} and deleted = 0
            """)
    void updateProvider(@Param("conversationId") Long conversationId,
                        @Param("providerType") String providerType,
                        @Param("providerKey") String providerKey,
                        @Param("now") LocalDateTime now);

    /** 归档会话，历史消息仍保留用于安全审计。 */
    @Update("""
            update chat_conversation
            set conversation_status = 'ARCHIVED', updated_at = #{now}
            where id = #{conversationId} and conversation_status = 'ACTIVE' and deleted = 0
            """)
    int archive(@Param("conversationId") Long conversationId, @Param("now") LocalDateTime now);

    /** 行程结束后转为历史群，保留成员、消息以及继续聊天能力。 */
    @Update("""
            update chat_conversation
            set conversation_status='HISTORY', updated_at=#{now}
            where id=#{conversationId} and conversation_status='ACTIVE' and deleted=0
            """)
    int markHistory(@Param("conversationId") Long conversationId, @Param("now") LocalDateTime now);
}
