package com.tongluxing.notify.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.notify.entity.AppPushDevice;
import com.tongluxing.notify.entity.AppPushTask;
import com.tongluxing.notify.service.PushGateway;

import lombok.RequiredArgsConstructor;

/**
 * 通用 HTTP 推送网关。
 *
 * <p>部署时把 {@code APP_PUSH_GATEWAY_URL} 指向 FCM/厂商推送聚合服务。该适配器只传递
 * 设备 Token 和业务负载，不在项目里硬编码任何厂商密钥。</p>
 */
@Component
@RequiredArgsConstructor
public class HttpPushGateway implements PushGateway {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    @Value("${app.push.gateway-url:}")
    private String gatewayUrl;

    @Value("${app.push.api-key:}")
    private String apiKey;

    @Override
    public void send(AppPushDevice device, AppPushTask task) {
        if (!StringUtils.hasText(gatewayUrl)) {
            throw new IllegalStateException("未配置 APP_PUSH_GATEWAY_URL，推送任务已保留等待重试");
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "userId", String.valueOf(task.getUserId()),
                    "deviceId", device.getDeviceId(),
                    "platform", device.getPlatform(),
                    "vendor", device.getVendor(),
                    "pushToken", device.getPushToken(),
                    "title", task.getTitle(),
                    "content", task.getContent(),
                    "payload", objectMapper.readTree(task.getPayloadJson())
            ));
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(gatewayUrl))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (StringUtils.hasText(apiKey)) {
                builder.header("Authorization", "Bearer " + apiKey.trim());
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("推送网关返回 HTTP " + response.statusCode());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("推送请求被中断", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("推送网关调用失败：" + ex.getMessage(), ex);
        }
    }
}
