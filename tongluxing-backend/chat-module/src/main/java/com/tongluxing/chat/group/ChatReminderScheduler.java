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

/**
 * 到期群提醒调度器。
 *
 * <p>提醒先通过条件更新抢占，再写入本地消息事实；这样即使应用多实例同时扫描，
 * 同一提醒也只有一个实例能够派发。腾讯 IM 仅承担实时通知，失败时本地消息仍会保留，
 * 用户重新打开会话后仍能读取提醒。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatReminderScheduler {
    private final ChatGroupMapper mapper;
    private final TencentImService tencentImService;
    private final ObjectMapper objectMapper;

    /**
     * 扫描并派发已经到期的提醒。
     * 初次启动延迟五秒，之后以上一次任务完成时间为基准每十五秒扫描一次，避免任务重叠。
     */
    @Scheduled(initialDelay = 5000, fixedDelay = 15000)
    @Transactional
    public void dispatchDueReminders() {
        // Mapper 只返回尚未派发且已经到期的提醒，减少 Java 层无效过滤。
        for (Map<String, Object> row : mapper.dueReminders()) {
            LocalDateTime now = LocalDateTime.now();
            Long itemId = ((Number) row.get("id")).longValue();
            // 条件更新相当于轻量级分布式锁；返回 0 表示已被另一个实例抢先处理。
            if (mapper.markReminderDispatched(itemId, now) == 0) continue;
            Long conversationId = ((Number) row.get("conversationId")).longValue();
            String detail = row.get("content") == null ? "" : row.get("content").toString().trim();
            String content = "【行程提醒】" + row.get("title") + (detail.isEmpty() ? "" : "\n" + detail);
            long messageId = SnowflakeIdGenerator.nextId();
            String providerKey = "system-reminder-" + messageId;
            try {
                // 只有真实腾讯 IM 会话且能确定群主发送身份时，才尝试实时云端投递。
                if ("TENCENT_IM".equals(row.get("providerType")) && tencentImService.isConfigured()
                        && row.get("ownerUserId") instanceof Number owner) {
                    providerKey = tencentImService.sendGroupText(row.get("providerConversationKey").toString(),
                            TencentImServiceImpl.toImUserId(owner.longValue()), content);
                }
            } catch (RuntimeException ex) {
                // 云端失败不抛出：providerKey 保持本地值，后续仍继续保存可审计消息。
                log.warn("Tencent IM reminder delivery failed; local message retained, itemId={}", itemId, ex);
            }
            try {
                // payload 同时保存展示文本和 reminderItemId，便于客户端跳转到提醒详情。
                mapper.insertReminderMessage(messageId, conversationId,
                        objectMapper.writeValueAsString(Map.of("content", content, "reminderItemId", itemId)),
                        providerKey, now);
            } catch (Exception ex) {
                throw new IllegalStateException("行程提醒消息序列化失败", ex);
            }
            // 最后刷新会话摘要和成员未读数，使列表能立即体现这条系统提醒。
            mapper.updateReminderPreview(conversationId, messageId, content, now);
            mapper.incrementReminderUnread(conversationId, now);
        }
    }
}
