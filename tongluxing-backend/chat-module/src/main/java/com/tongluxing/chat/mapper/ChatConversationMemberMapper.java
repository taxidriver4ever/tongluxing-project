package com.tongluxing.chat.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import com.tongluxing.chat.entity.ChatConversationMember;

/**
 * 聊天会话成员 Mapper。
 */
@Mapper
public interface ChatConversationMemberMapper {

    /** 查询指定会话中的指定用户成员记录。 */
    @Select("""
            select id, conversation_id, user_id, member_role, member_status, unread_count, muted_flag, pinned_flag,
                   last_read_message_id, cleared_before_message_id, joined_at, exited_at, created_at, updated_at, deleted
            from chat_conversation_member
            where conversation_id = #{conversationId} and user_id = #{userId} and deleted = 0
            limit 1
            """)
    ChatConversationMember findByConversationAndUser(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    /** 查询指定会话的有效成员列表。 */
    @Select("""
            select id, conversation_id, user_id, member_role, member_status, unread_count, muted_flag, pinned_flag,
                   last_read_message_id, cleared_before_message_id, joined_at, exited_at, created_at, updated_at, deleted
            from chat_conversation_member
            where conversation_id = #{conversationId} and member_status = 'ACTIVE' and deleted = 0
            """)
    List<ChatConversationMember> findActiveByConversationId(@Param("conversationId") Long conversationId);

    /** 查询私聊中的另一位有效成员。 */
    @Select("""
            select id, conversation_id, user_id, member_role, member_status, unread_count, muted_flag, pinned_flag,
                   last_read_message_id, cleared_before_message_id, joined_at, exited_at, created_at, updated_at, deleted
            from chat_conversation_member
            where conversation_id=#{conversationId} and user_id<>#{userId}
              and member_status='ACTIVE' and deleted=0
            limit 1
            """)
    ChatConversationMember findOtherActive(@Param("conversationId") Long conversationId,
                                           @Param("userId") Long userId);

    /** 判断两位用户当前是否共享有效的行程/车队会话。 */
    @Select("""
            select count(*)
            from chat_conversation_member me
            join chat_conversation_member peer on peer.conversation_id=me.conversation_id
              and peer.user_id=#{peerUserId} and peer.member_status='ACTIVE' and peer.deleted=0
            join chat_conversation c on c.id=me.conversation_id and c.deleted=0
              and c.biz_type in ('TRIP','TEAM') and c.conversation_status in ('ACTIVE','HISTORY')
            where me.user_id=#{userId} and me.member_status='ACTIVE' and me.deleted=0
            """)
    int countSharedTrip(@Param("userId") Long userId, @Param("peerUserId") Long peerUserId);

    /** 本地清空聊天记录：只更新当前成员的可见消息截止点。 */
    @Update("""
            update chat_conversation_member
            set cleared_before_message_id=#{messageId}, unread_count=0, updated_at=#{now}
            where conversation_id=#{conversationId} and user_id=#{userId}
              and member_status='ACTIVE' and deleted=0
            """)
    int clearLocalMessages(@Param("conversationId") Long conversationId,
                           @Param("userId") Long userId,
                           @Param("messageId") Long messageId,
                           @Param("now") LocalDateTime now);

    /** 拉取消息后清除未读数。 */
    @Update("""
            update chat_conversation_member
            set unread_count=0, last_read_message_id=#{messageId}, updated_at=#{now}
            where conversation_id=#{conversationId} and user_id=#{userId}
              and member_status='ACTIVE' and deleted=0
            """)
    int markRead(@Param("conversationId") Long conversationId,
                 @Param("userId") Long userId,
                 @Param("messageId") Long messageId,
                 @Param("now") LocalDateTime now);

    /** 新增会话成员。 */
    @Insert("""
            insert into chat_conversation_member
                (id, conversation_id, user_id, member_role, member_status, unread_count, muted_flag, pinned_flag,
                 last_read_message_id, cleared_before_message_id, joined_at, exited_at, created_at, updated_at, deleted)
            values
                (#{id}, #{conversationId}, #{userId}, #{memberRole}, #{memberStatus}, #{unreadCount}, 0, 0,
                 #{lastReadMessageId}, #{clearedBeforeMessageId}, #{joinedAt}, #{exitedAt}, #{createdAt}, #{updatedAt}, 0)
            """)
    void insert(ChatConversationMember member);

    /** 将成员状态更新为已退出。 */
    @Update("""
            update chat_conversation_member
            set member_status = 'EXITED', exited_at = #{now}, updated_at = #{now}
            where conversation_id = #{conversationId} and user_id = #{userId}
              and member_status = 'ACTIVE' and deleted = 0
            """)
    int exit(@Param("conversationId") Long conversationId, @Param("userId") Long userId, @Param("now") LocalDateTime now);

    /** 给除发送者以外的有效成员增加未读数。 */
    @Update("""
            update chat_conversation_member
            set unread_count = unread_count + 1, updated_at = #{now}
            where conversation_id = #{conversationId} and user_id <> #{senderUserId}
              and member_status = 'ACTIVE' and deleted = 0
            """)
    void incrementUnread(@Param("conversationId") Long conversationId, @Param("senderUserId") Long senderUserId, @Param("now") LocalDateTime now);

    /** 更新当前成员自己的免打扰与置顶设置。 */
    @Update("""
            update chat_conversation_member
            set muted_flag=#{muted}, pinned_flag=#{pinned}, updated_at=#{now}
            where conversation_id=#{conversationId} and user_id=#{userId}
              and member_status='ACTIVE' and deleted=0
            """)
    int updateSettings(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                       @Param("muted") boolean muted, @Param("pinned") boolean pinned,
                       @Param("now") LocalDateTime now);

    /** 重新激活已退出的成员。 */
    @Update("""
            update chat_conversation_member
            set member_role = #{role},
                member_status = 'ACTIVE',
                unread_count = 0,
                cleared_before_message_id = null,
                joined_at = #{now},
                exited_at = null,
                updated_at = #{now}
            where conversation_id = #{conversationId} and user_id = #{userId} and deleted = 0
            """)
    int reactivate(@Param("conversationId") Long conversationId,
                   @Param("userId") Long userId,
                   @Param("role") String role,
                   @Param("now") LocalDateTime now);

    /** 行程结束时退出该会话的全部有效成员。 */
    @Update("""
            update chat_conversation_member
            set member_status = 'EXITED', exited_at = #{now}, updated_at = #{now}
            where conversation_id = #{conversationId} and member_status = 'ACTIVE' and deleted = 0
            """)
    int exitAll(@Param("conversationId") Long conversationId, @Param("now") LocalDateTime now);
}
