package com.tongluxing.chat.callback;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 在独立线程池和事务中消费腾讯 IM 的通知型回调。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TencentImCallbackAsyncProcessor {

    private final ObjectProvider<TencentImCallbackService> callbackServiceProvider;

    @Async("tencentImCallbackExecutor")
    @Transactional
    public void process(String command, Map<String, Object> body) {
        TencentImCallbackService callbackService = callbackServiceProvider.getObject();
        try {
            callbackService.handleAsync(command, body);
        } catch (RuntimeException exception) {
            String groupId = string(body.get("GroupId"));
            String fromAccount = string(body.get("From_Account"));
            String toAccount = string(body.get("To_Account"));
            String scope = StringUtils.hasText(groupId)
                    ? groupId : fromAccount + "->" + toAccount;
            String providerKey = command != null && command.endsWith("CallbackAfterSendMsg")
                    ? callbackService.providerMessageKey(command, body) : "";
            log.error(
                    "Tencent IM async callback failed, command={}, providerKey={}, scope={}, "
                            + "groupId={}, fromAccount={}, toAccount={}, msgKey={}, msgId={}",
                    command, providerKey, scope, groupId, fromAccount, toAccount,
                    string(body.get("MsgKey")), string(body.get("MsgId")), exception);
            // ACK 已返回；重新抛出只用于回滚 Worker 事务和触发异步异常记录。
            throw exception;
        }
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
