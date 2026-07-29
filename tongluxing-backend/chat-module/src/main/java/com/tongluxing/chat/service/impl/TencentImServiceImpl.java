package com.tongluxing.chat.service.impl;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

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

    /** 判断云端必要配置是否完整；未配置时业务层使用本地同步模式。 */
    @Override
    public boolean isConfigured() {
        return "TENCENT_IM".equals(providerType()) && properties.hasCredentials();
    }

    /** 返回经过标准化的显式聊天通道配置。 */
    @Override
    public String providerType() {
        return properties.normalizedProviderType();
    }

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
        importAccount(imUserId);
        String userSig = generateUserSig(imUserId);
        Long expireTime = Instant.now().getEpochSecond() + properties.getExpireSeconds();
        return new ImUserSigResponse(properties.getSdkAppId(), imUserId, userSig, expireTime);
    }

    /** 在腾讯云 IM 创建公开群组。 */
    @Override
    public void createGroup(String groupId, String ownerUserId, String groupName) {
        importAccount(ownerUserId);
        Map<String, Object> payload = Map.of(
                "Owner_Account", ownerUserId,
                "Type", "Public",
                "Name", groupName,
                "GroupId", groupId
        );
        // 10025 表示 GroupId 已存在；迁移重试时按幂等成功处理。
        callRest("group_open_http_svc/create_group", payload, Set.of(10025));
    }

    /** 销毁腾讯云 IM 群组。 */
    @Override
    public void destroyGroup(String groupId) {
        callRest("group_open_http_svc/destroy_group", Map.of("GroupId", groupId));
    }

    /** 添加腾讯云 IM 群成员。 */
    @Override
    public void addGroupMember(String groupId, String userId) {
        importAccount(userId);
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

    /** 通过腾讯 IM REST API 发送群文本，并返回本次请求随机号作为本地关联 key。 */
    @Override
    public String sendGroupText(String groupId, String senderUserId, String content) {
        importAccount(senderUserId);
        int random = ThreadLocalRandom.current().nextInt(100000, Integer.MAX_VALUE);
        Map<String, Object> payload = Map.of(
                "GroupId", groupId,
                "Random", random,
                "From_Account", senderUserId,
                "MsgBody", List.of(Map.of(
                        "MsgType", "TIMTextElem",
                        "MsgContent", Map.of("Text", content)
                ))
        );
        callRest("group_open_http_svc/send_group_msg", payload);
        return "tencent-" + random;
    }

    /** 通过腾讯 IM REST API 发送单聊文本，并返回腾讯侧随机号作为本地关联 key。 */
    @Override
    public String sendC2CText(String receiverUserId, String senderUserId, String content) {
        importAccount(senderUserId);
        importAccount(receiverUserId);
        int random = ThreadLocalRandom.current().nextInt(100000, Integer.MAX_VALUE);
        Map<String, Object> payload = Map.of(
                "SyncOtherMachine", 2,
                "From_Account", senderUserId,
                "To_Account", receiverUserId,
                "MsgRandom", random,
                "MsgTimeStamp", Instant.now().getEpochSecond(),
                "MsgBody", List.of(Map.of(
                        "MsgType", "TIMTextElem",
                        "MsgContent", Map.of("Text", content)
                ))
        );
        callRest("openim/sendmsg", payload);
        return "tencent-" + random;
    }

    /** 将业务用户幂等导入腾讯 IM，避免建群或加群时出现 invalid owner/member id。 */
    private void importAccount(String userId) {
        callRest("im_open_login_svc/account_import", Map.of(
                "Identifier", userId,
                "Nick", "同路行用户"
        ));
    }

    /** 将业务用户 ID 转换为腾讯云 IM 用户 ID。 */
    public static String toImUserId(Long userId) {
        return "u_" + userId;
    }

    /** 调用腾讯云 IM REST API，并校验 HTTP 与业务响应状态。 */
    private void callRest(String command, Map<String, Object> payload) {
        callRest(command, payload, Set.of());
    }

    /** 调用腾讯云 REST API，并允许部分业务错误码按幂等成功处理。 */
    private void callRest(String command, Map<String, Object> payload, Set<Integer> acceptedErrorCodes) {
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
                Object errorCode = body.get("ErrorCode");
                if (errorCode instanceof Number number && acceptedErrorCodes.contains(number.intValue())) {
                    return;
                }
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
        if (!isConfigured()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "腾讯云 IM 配置未完成");
        }
    }
}
