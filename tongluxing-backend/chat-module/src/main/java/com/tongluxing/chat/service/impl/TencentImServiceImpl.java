package com.tongluxing.chat.service.impl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tencentyun.TLSSigAPIv2;
import com.tongluxing.chat.config.TencentImProperties;
import com.tongluxing.chat.service.TencentImService;
import com.tongluxing.chat.vo.ImUserSigResponse;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

/**
 * 腾讯云 IM 集成服务实现。
 *
 * <p>负责生成 UserSig，并通过腾讯云 REST API 同步群组和成员。业务数据仍由 chat-module 本地表保存。</p>
 */
@Service
@RequiredArgsConstructor
public class TencentImServiceImpl implements TencentImService {

    /** 腾讯云 IM REST API 基础地址。 */
    private static final String REST_BASE_URL = "https://console.tim.qq.com/v4/";

    /** 腾讯云 IM 配置。 */
    private final TencentImProperties properties;
    /** 当前登录用户上下文。 */
    private final CurrentUserContext currentUserContext;
    /** JSON 序列化工具。 */
    private final ObjectMapper objectMapper;
    /** JDK HTTP 客户端，用于调用腾讯云 IM REST API。 */
    private final HttpClient httpClient = HttpClient.newHttpClient();

    /** 为指定 IM 用户 ID 生成 UserSig。 */
    @Override
    public String generateUserSig(String userId) {
        validateConfig();
        TLSSigAPIv2 api = new TLSSigAPIv2(properties.getSdkAppId(), properties.getSecretKey());
        return api.genUserSig(userId, properties.getExpireSeconds());
    }

    /** 为当前登录用户生成 IM 登录票据。 */
    @Override
    public ImUserSigResponse generateCurrentUserSig() {
        Long userId = currentUserContext.requireUserId();
        String imUserId = toImUserId(userId);
        String userSig = generateUserSig(imUserId);
        Long expireTime = Instant.now().getEpochSecond() + properties.getExpireSeconds();
        return new ImUserSigResponse(properties.getSdkAppId(), imUserId, userSig, expireTime);
    }

    /** 在腾讯云 IM 创建公开群组。 */
    @Override
    public void createGroup(String groupId, String ownerUserId, String groupName) {
        Map<String, Object> payload = Map.of(
                "Owner_Account", ownerUserId,
                "Type", "Public",
                "Name", groupName,
                "GroupId", groupId
        );
        callRest("group_open_http_svc/create_group", payload);
    }

    /** 销毁腾讯云 IM 群组。 */
    @Override
    public void destroyGroup(String groupId) {
        callRest("group_open_http_svc/destroy_group", Map.of("GroupId", groupId));
    }

    /** 添加腾讯云 IM 群成员。 */
    @Override
    public void addGroupMember(String groupId, String userId) {
        Map<String, Object> payload = Map.of(
                "GroupId", groupId,
                "MemberList", List.of(Map.of("Member_Account", userId))
        );
        callRest("group_open_http_svc/add_group_member", payload);
    }

    /** 删除腾讯云 IM 群成员。 */
    @Override
    public void removeGroupMember(String groupId, String userId) {
        Map<String, Object> payload = Map.of(
                "GroupId", groupId,
                "MemberToDel_Account", List.of(userId)
        );
        callRest("group_open_http_svc/delete_group_member", payload);
    }

    /** 将业务用户 ID 转换为腾讯云 IM 用户 ID。 */
    public static String toImUserId(Long userId) {
        return "u_" + userId;
    }

    /** 调用腾讯云 IM REST API，并校验 HTTP 与业务响应状态。 */
    private void callRest(String command, Map<String, Object> payload) {
        validateConfig();
        String adminSig = generateUserSig(properties.getAdminUserId());
        String url = REST_BASE_URL + command
                + "?sdkappid=" + properties.getSdkAppId()
                + "&identifier=" + properties.getAdminUserId()
                + "&usersig=" + adminSig
                + "&random=" + ThreadLocalRandom.current().nextInt(100000, 999999999)
                + "&contenttype=json";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "腾讯云 IM 请求失败");
            }
            // 腾讯云 IM 业务成功以 ActionStatus=OK 表示，HTTP 200 不等于业务成功。
            Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            Object actionStatus = body.get("ActionStatus");
            if (!"OK".equals(actionStatus)) {
                Object errorInfo = body.get("ErrorInfo");
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "腾讯云 IM 操作失败：" + (errorInfo == null ? "unknown" : errorInfo));
            }
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "腾讯云 IM 请求序列化失败");
        } catch (IOException exception) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "腾讯云 IM 网络请求失败");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "腾讯云 IM 请求被中断");
        }
    }

    /** 校验腾讯云 IM 必要配置是否完整。 */
    private void validateConfig() {
        if (properties.getSdkAppId() == null || properties.getSdkAppId() <= 0
                || !StringUtils.hasText(properties.getSecretKey())
                || !StringUtils.hasText(properties.getAdminUserId())
                || properties.getExpireSeconds() == null || properties.getExpireSeconds() <= 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "腾讯云 IM 配置未完成");
        }
    }
}
