package com.tongdao.chat.service.impl;

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
import com.tongdao.chat.config.TencentImProperties;
import com.tongdao.chat.service.TencentImService;
import com.tongdao.chat.vo.ImUserSigResponse;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.user.support.CurrentUserContext;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TencentImServiceImpl implements TencentImService {

    private static final String REST_BASE_URL = "https://console.tim.qq.com/v4/";

    private final TencentImProperties properties;
    private final CurrentUserContext currentUserContext;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Override
    public String generateUserSig(String userId) {
        validateConfig();
        TLSSigAPIv2 api = new TLSSigAPIv2(properties.getSdkAppId(), properties.getSecretKey());
        return api.genUserSig(userId, properties.getExpireSeconds());
    }

    @Override
    public ImUserSigResponse generateCurrentUserSig() {
        Long userId = currentUserContext.requireUserId();
        String imUserId = toImUserId(userId);
        String userSig = generateUserSig(imUserId);
        Long expireTime = Instant.now().getEpochSecond() + properties.getExpireSeconds();
        return new ImUserSigResponse(properties.getSdkAppId(), imUserId, userSig, expireTime);
    }

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

    @Override
    public void destroyGroup(String groupId) {
        callRest("group_open_http_svc/destroy_group", Map.of("GroupId", groupId));
    }

    @Override
    public void addGroupMember(String groupId, String userId) {
        Map<String, Object> payload = Map.of(
                "GroupId", groupId,
                "MemberList", List.of(Map.of("Member_Account", userId))
        );
        callRest("group_open_http_svc/add_group_member", payload);
    }

    @Override
    public void removeGroupMember(String groupId, String userId) {
        Map<String, Object> payload = Map.of(
                "GroupId", groupId,
                "MemberToDel_Account", List.of(userId)
        );
        callRest("group_open_http_svc/delete_group_member", payload);
    }

    public static String toImUserId(Long userId) {
        return "u_" + userId;
    }

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

    private void validateConfig() {
        if (properties.getSdkAppId() == null || properties.getSdkAppId() <= 0
                || !StringUtils.hasText(properties.getSecretKey())
                || !StringUtils.hasText(properties.getAdminUserId())
                || properties.getExpireSeconds() == null || properties.getExpireSeconds() <= 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "腾讯云 IM 配置未完成");
        }
    }
}
