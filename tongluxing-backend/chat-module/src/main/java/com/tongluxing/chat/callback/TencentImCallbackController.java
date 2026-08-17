package com.tongluxing.chat.callback;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/** 腾讯 IM 统一回调入口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/callbacks/tencent-im")
public class TencentImCallbackController {

    private final TencentImCallbackService callbackService;

    /**
     * 接收腾讯 IM 回调。
     *
     * <p>该接口必须直接返回腾讯 IM 规定的 ActionStatus/ErrorCode 结构，不能套用项目 Result 包装。</p>
     */
    @PostMapping
    public Map<String, Object> callback(
            @RequestParam("SdkAppid") Long sdkAppId,
            @RequestParam("CallbackCommand") String callbackCommand,
            @RequestParam("RequestTime") Long requestTime,
            @RequestParam("Sign") String sign,
            @RequestBody Map<String, Object> body) {
        // 先完成来源鉴权，再进行任何数据库写入。
        callbackService.verify(sdkAppId, requestTime, sign);
        if (callbackService.isBlockingCallback(callbackCommand)) {
            return callbackService.handleBlocking(callbackCommand, body);
        }
        // After/Event 回调不影响腾讯 IM 当前操作，提交独立线程池后立即 ACK。
        callbackService.submitAsync(callbackCommand, body);
        return callbackService.allow();
    }
}
