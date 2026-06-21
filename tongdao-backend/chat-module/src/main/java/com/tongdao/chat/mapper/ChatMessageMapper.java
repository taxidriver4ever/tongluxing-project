package com.tongdao.chat.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.tongdao.chat.entity.ChatMessage;

@Mapper
public interface ChatMessageMapper {

    List<ChatMessage> findMessages(@Param("conversationId") Long conversationId,
                                   @Param("beforeMessageId") Long beforeMessageId,
                                   @Param("limit") Integer limit);

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
