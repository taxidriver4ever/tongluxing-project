package com.tongluxing.chat.group;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.chat.service.TencentImService;
import com.tongluxing.chat.service.impl.TencentImServiceImpl;
import com.tongluxing.common.utils.SnowflakeIdGenerator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 将到期的行程提醒转换为可审计的群系统消息。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatReminderScheduler {
    private final ChatGroupMapper mapper;
    private final TencentImService tencentImService;
    private final ObjectMapper objectMapper;

    @Scheduled(initialDelay = 5000, fixedDelay = 15000)
    @Transactional
    public void dispatchDueReminders() {
        for (Map<String, Object> row : mapper.dueReminders()) {
            LocalDateTime now = LocalDateTime.now();
            Long itemId = ((Number) row.get("id")).longValue();
            if (mapper.markReminderDispatched(itemId, now) == 0) continue;
            Long conversationId = ((Number) row.get("conversationId")).longValue();
            String detail = row.get("content") == null ? "" : row.get("content").toString().trim();
            String content = "【行程提醒】" + row.get("title") + (detail.isEmpty() ? "" : "\n" + detail);
            long messageId = SnowflakeIdGenerator.nextId();
            String providerKey = "system-reminder-" + messageId;
            try {
                if ("TENCENT_IM".equals(row.get("providerType")) && tencentImService.isConfigured()
                        && row.get("ownerUserId") instanceof Number owner) {
                    providerKey = tencentImService.sendGroupText(row.get("providerConversationKey").toString(),
                            TencentImServiceImpl.toImUserId(owner.longValue()), content);
                }
            } catch (RuntimeException ex) {
                log.warn("Tencent IM reminder delivery failed; local message retained, itemId={}", itemId, ex);
            }
            try {
                mapper.insertReminderMessage(messageId, conversationId,
                        objectMapper.writeValueAsString(Map.of("content", content, "reminderItemId", itemId)),
                        providerKey, now);
            } catch (Exception ex) {
                throw new IllegalStateException("行程提醒消息序列化失败", ex);
            }
            mapper.updateReminderPreview(conversationId, messageId, content, now);
            mapper.incrementReminderUnread(conversationId, now);
        }
    }
}
