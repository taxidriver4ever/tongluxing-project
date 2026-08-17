package com.tongluxing.chat.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.chat.config.TencentImCallbackAsyncConfiguration;
import com.tongluxing.chat.config.TencentImProperties;
import com.tongluxing.chat.entity.ChatConversation;
import com.tongluxing.chat.mapper.ChatConversationMapper;
import com.tongluxing.chat.mapper.ChatConversationMemberMapper;
import com.tongluxing.chat.mapper.ChatMessageMapper;
import com.tongluxing.chat.mapper.MessageRiskMapper;
import com.tongluxing.storage.service.StorageService;
import com.tongluxing.user.mapper.UserFollowMapper;

import jakarta.annotation.Resource;

@SpringJUnitConfig(TencentImCallbackServiceTest.TestConfiguration.class)
class TencentImCallbackServiceTest {

    @Resource
    private TencentImCallbackService service;
    @Resource
    private ChatConversationMapper conversationMapper;
    @Resource
    private ChatConversationMemberMapper memberMapper;
    @Resource
    private ChatMessageMapper messageMapper;

    @BeforeEach
    void resetMocks() {
        reset(conversationMapper, memberMapper, messageMapper);
    }

    @Test
    void beforeSendRemainsSynchronousAndRejectsRiskyMessage() {
        when(conversationMapper.findByProviderConversationKey("group-1")).thenReturn(conversation());
        var member = new com.tongluxing.chat.entity.ChatConversationMember();
        member.setMemberStatus("ACTIVE");
        when(memberMapper.findByConversationAndUser(10L, 1L)).thenReturn(member);

        Map<String, Object> decision = service.handleBlocking(
                "Group.CallbackBeforeSendMsg", groupMessage("blocked", null, null, "请先转账保证金"));

        assertEquals(1, decision.get("ErrorCode"));
    }

    @Test
    void afterSendSubmissionReturnsBeforeSlowWorkerFinishes() throws Exception {
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        CountDownLatch workerFinished = new CountDownLatch(1);
        when(conversationMapper.findByProviderConversationKey("group-1")).thenAnswer(invocation -> {
            workerStarted.countDown();
            releaseWorker.await(2, TimeUnit.SECONDS);
            workerFinished.countDown();
            return null;
        });

        long startedAt = System.nanoTime();
        service.submitAsync("Group.CallbackAfterSendMsg", groupMessage("slow", null, null, "hello"));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertTrue(elapsedMillis < 500, "异步提交不应等待慢 Worker，实际耗时=" + elapsedMillis + "ms");
        assertTrue(workerStarted.await(1, TimeUnit.SECONDS));
        assertEquals(1L, workerFinished.getCount());
        releaseWorker.countDown();
        assertTrue(workerFinished.await(2, TimeUnit.SECONDS));
    }

    @Test
    void sameMsgKeyIsStoredOnlyOnceByApplicationDedup() {
        prepareConversation();
        when(messageMapper.countByProviderMessageKey(any())).thenReturn(0, 1);
        Map<String, Object> body = groupMessage("same-key", null, null, "hello");

        service.handleAsync("Group.CallbackAfterSendMsg", body);
        service.handleAsync("Group.CallbackAfterSendMsg", body);

        verify(messageMapper).insert(any());
    }

    @Test
    void concurrentDuplicateKeyRaceIsTreatedAsIdempotentSuccess() throws Exception {
        prepareConversation();
        when(messageMapper.countByProviderMessageKey(any())).thenReturn(0);
        AtomicBoolean inserted = new AtomicBoolean();
        AtomicInteger successfulInserts = new AtomicInteger();
        doAnswer(invocation -> {
            if (inserted.compareAndSet(false, true)) {
                successfulInserts.incrementAndGet();
                return 1;
            }
            throw new DuplicateKeyException("provider_message_key unique conflict");
        }).when(messageMapper).insert(any());

        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Future<?>> futures = new ArrayList<>();
            Map<String, Object> body = groupMessage("concurrent-key", null, null, "hello");
            for (int index = 0; index < 20; index++) {
                futures.add(executor.submit(() -> service.handleAsync("Group.CallbackAfterSendMsg", body)));
            }
            for (Future<?> future : futures) future.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, successfulInserts.get());
    }

    @Test
    void msgIdIsUsedWhenMsgKeyIsMissing() {
        String key = service.providerMessageKey(
                "Group.CallbackAfterSendMsg", groupMessage(null, "msg-id-7", null, "hello"));

        assertEquals("Group.CallbackAfterSendMsg:group-1:msg-id-7", key);
    }

    @Test
    void sequenceAndRandomFormStableScopedFallback() {
        Map<String, Object> body = groupMessage(null, null, "99", "hello");

        String first = service.providerMessageKey("Group.CallbackAfterSendMsg", body);
        String second = service.providerMessageKey("Group.CallbackAfterSendMsg", body);

        assertEquals("Group.CallbackAfterSendMsg:group-1:7:99", first);
        assertEquals(first, second);
    }

    private void prepareConversation() {
        when(conversationMapper.findByProviderConversationKey("group-1")).thenReturn(conversation());
    }

    private ChatConversation conversation() {
        ChatConversation conversation = new ChatConversation();
        conversation.setId(10L);
        conversation.setConversationStatus("ACTIVE");
        return conversation;
    }

    private Map<String, Object> groupMessage(
            String msgKey, String msgId, String msgRandom, String text) {
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("GroupId", "group-1");
        body.put("From_Account", "u_1");
        body.put("MsgSeq", 7);
        body.put("MsgTime", Instant.now().getEpochSecond());
        body.put("MsgBody", List.of(Map.of(
                "MsgType", "TIMTextElem",
                "MsgContent", Map.of("Text", text))));
        if (msgKey != null) body.put("MsgKey", msgKey);
        if (msgId != null) body.put("MsgId", msgId);
        if (msgRandom != null) body.put("MsgRandom", msgRandom);
        return body;
    }

    @Configuration
    @Import({
            TencentImCallbackAsyncConfiguration.class,
            TencentImCallbackAsyncProcessor.class,
            TencentImCallbackService.class
    })
    static class TestConfiguration {
        @Bean
        TencentImProperties properties() {
            TencentImProperties properties = new TencentImProperties();
            properties.setSdkAppId(1L);
            properties.setSecretKey("test-secret");
            properties.setAdminUserId("admin");
            properties.setCallbackToken("test-token");
            properties.setExpireSeconds(3600L);
            return properties;
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean ChatConversationMapper conversationMapper() { return mock(ChatConversationMapper.class); }
        @Bean ChatConversationMemberMapper memberMapper() { return mock(ChatConversationMemberMapper.class); }
        @Bean ChatMessageMapper messageMapper() { return mock(ChatMessageMapper.class); }
        @Bean MessageRiskMapper riskMapper() { return mock(MessageRiskMapper.class); }
        @Bean UserFollowMapper userFollowMapper() { return mock(UserFollowMapper.class); }
        @Bean StorageService storageService() { return mock(StorageService.class); }
    }
}
