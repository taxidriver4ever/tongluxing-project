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
        // provider 类型和凭据必须同时满足；仅填写密钥但显式选择 MOCK 时也不会访问公网。
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
        // UserSig 相当于腾讯 IM 登录凭证，生成前必须拒绝缺失或占位配置。
        validateConfig();
        TLSSigAPIv2 api = new TLSSigAPIv2(properties.getSdkAppId(), properties.getSecretKey());
        return api.genUserSig(userId, properties.getExpireSeconds());
    }

    /** 为当前登录用户生成 IM 登录票据。 */
    @Override
    public ImUserSigResponse generateCurrentUserSig() {
        Long userId = currentUserContext.requireUserId();
        // 使用固定前缀隔离业务用户 ID 与腾讯控制台中可能存在的其他账号体系。
        String imUserId = toImUserId(userId);
        // account_import 是幂等操作，先导入可避免客户端首次登录时出现账号不存在。
        importAccount(imUserId);
        String userSig = generateUserSig(imUserId);
        Long expireTime = Instant.now().getEpochSecond() + properties.getExpireSeconds();
        return new ImUserSigResponse(properties.getSdkAppId(), imUserId, userSig, expireTime);
    }

    /** 在腾讯云 IM 创建公开群组。 */
    @Override
    public void createGroup(String groupId, String ownerUserId, String groupName) {
        // 腾讯云要求 Owner_Account 已存在，因此建群前先执行幂等账号导入。
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
        // 与群主相同，普通成员也必须先导入腾讯账号体系才能被加入群组。
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
        // Random 用于腾讯侧消息去重；同时作为本地 providerMessageKey 的关联依据。
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
        // 双方账号都先导入，避免新用户首次私聊只有其中一端存在而投递失败。
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
        // 重复导入同一 Identifier 会更新/复用账号，不需要额外查询云端是否存在。
        callRest("im_open_login_svc/account_import", Map.of(
                "Identifier", userId,
                "Nick", "同路行用户"
        ));
    }

    /** 将业务用户 ID 转换为腾讯云 IM 用户 ID。 */
    public static String toImUserId(Long userId) {
        // 该映射仅用于腾讯云通道；本地数据库关联仍始终使用原始 Long userId。
        return "u_" + userId;
    }

    /** 调用腾讯云 IM REST API，并校验 HTTP 与业务响应状态。 */
    private void callRest(String command, Map<String, Object> payload) {
        callRest(command, payload, Set.of());
    }

    /** 调用腾讯云 REST API，并允许部分业务错误码按幂等成功处理。 */
    private void callRest(String command, Map<String, Object> payload, Set<Integer> acceptedErrorCodes) {
        validateConfig();
        // REST 管理接口必须用管理员 UserSig 鉴权，不能复用当前 App 用户的 UserSig。
        String adminSig = generateUserSig(properties.getAdminUserId());
        String url = REST_BASE_URL + command
                + "?sdkappid=" + properties.getSdkAppId()
                + "&identifier=" + properties.getAdminUserId()
                + "&usersig=" + adminSig
                + "&random=" + ThreadLocalRandom.current().nextInt(100000, 999999999)
                + "&contenttype=json";
        try {
            // 每个调用独立序列化请求体，避免共享可变 JSON 状态造成并发污染。
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            // 先检查传输层状态，再解析腾讯云响应中的业务状态。
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "腾讯云 IM 请求失败");
            }
            // 腾讯云 IM 业务成功以 ActionStatus=OK 表示，HTTP 200 不等于业务成功。
            Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {
            });
            Object actionStatus = body.get("ActionStatus");
            if (!"OK".equals(actionStatus)) {
                Object errorCode = body.get("ErrorCode");
                // 建群等幂等场景可把“资源已存在”视为成功，其余错误仍向业务层抛出。
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
            // 恢复中断标记，避免上层线程池无法感知取消信号。
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
