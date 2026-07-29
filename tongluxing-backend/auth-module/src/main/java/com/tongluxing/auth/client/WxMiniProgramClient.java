package com.tongluxing.auth.client;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tongluxing.auth.config.WxMiniProgramProperties;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;

import lombok.RequiredArgsConstructor;

/**
 * 微信小程序服务端接口客户端。
 *
 * <p>当前只封装登录链路需要的两个微信接口：获取 access_token、通过手机号授权 code 获取手机号。</p>
 *
 * <p>微信业务错误码、空响应与网络异常统一转换为 BusinessException，避免上层依赖微信响应结构。
 * appSecret 只作为服务端换取 access_token 的查询参数使用，不应进入业务日志。当前每次手机号授权
 * 都实时获取 access_token；调用量增长后应缓存并按微信返回的 expires_in 提前刷新。</p>
 */
@Component
@RequiredArgsConstructor
public class WxMiniProgramClient {

    /** 微信开放接口基础地址。 */
    private static final String WECHAT_API_BASE_URL = "https://api.weixin.qq.com";

    /** 小程序 appId/appSecret 配置。 */
    private final WxMiniProgramProperties properties;
    /** RestClient 固定绑定微信 API 域名，避免业务层拼接完整 URL。 */
    private final RestClient restClient = RestClient.create(WECHAT_API_BASE_URL);

    /**
     * 根据小程序端传来的手机号授权 code 获取用户手机号。
     *
     * @param code 微信小程序 wx.getPhoneNumber 返回的一次性 code
     * @return 微信返回的完整手机号
     */
    public String getPhoneNumber(String code) {
        // 微信手机号接口要求服务端 access_token，先使用 appId/appSecret 获取调用凭证。
        String accessToken = getAccessToken();
        try {
            // 授权 code 放在 JSON 请求体，access_token 按微信协议放在查询参数。
            PhoneNumberResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/wxa/business/getuserphonenumber")
                            .queryParam("access_token", accessToken)
                            .build())
                    .body(new PhoneNumberRequest(code))
                    .retrieve()
                    .body(PhoneNumberResponse.class);

            // HTTP 成功但响应体为空仍属于授权失败，不能继续创建账号。
            if (response == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信手机号授权失败");
            }
            // 微信可能以 HTTP 200 返回业务错误，必须显式检查 errcode。
            if (response.errcode() != null && response.errcode() != 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信手机号授权失败：" + response.errmsg());
            }
            // 成功响应必须包含完整手机号；缺失时拒绝进入认证公共流程。
            if (response.phoneInfo() == null || !StringUtils.hasText(response.phoneInfo().phoneNumber())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信未返回手机号");
            }
            // 只把经过微信响应校验的手机号交给 Service，隐藏第三方协议对象。
            return response.phoneInfo().phoneNumber();
        } catch (RestClientException exception) {
            // 网络、超时和 HTTP 协议异常统一转换为业务异常，Controller 不感知 RestClient。
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "微信手机号授权失败", exception);
        }
    }

    /**
     * 获取微信接口调用凭证。
     *
     * <p>这里保持简单直连微信；如果后续调用量变大，可以增加 Redis 缓存 access_token。</p>
     */
    private String getAccessToken() {
        // 缺少服务端配置时立即失败，避免向微信发送空凭据并产生难以定位的错误。
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppSecret())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "微信小程序 appId 或 appSecret 未配置");
        }

        try {
            // 按 client_credential 协议使用 appId/appSecret 换取服务端调用凭证。
            AccessTokenResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cgi-bin/token")
                            .queryParam("grant_type", "client_credential")
                            .queryParam("appid", properties.getAppId())
                            .queryParam("secret", properties.getAppSecret())
                            .build())
                    .retrieve()
                    .body(AccessTokenResponse.class);

            // 空响应无法判断凭证有效期和错误原因，统一按获取失败处理。
            if (response == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信 access_token 获取失败");
            }
            // 微信业务错误可能仍返回 2xx，不能只依赖 RestClient 的 HTTP 异常。
            if (response.errcode() != null && response.errcode() != 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信 access_token 获取失败：" + response.errmsg());
            }
            // errcode 成功但 token 文本为空同样不可用于后续手机号接口。
            if (!StringUtils.hasText(response.accessToken())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信 access_token 为空");
            }
            // 当前实现直接返回；未来缓存时应使用 expiresIn 提前刷新而非等到自然过期。
            return response.accessToken();
        } catch (RestClientException exception) {
            // 保留原始异常作为 cause 供服务端排查，对客户端只暴露稳定提示。
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "微信 access_token 获取失败", exception);
        }
    }

    /**
     * 微信获取 access_token 接口响应。
     *
     * @param accessToken 成功时的接口调用凭证
     * @param expiresIn 微信声明的有效期秒数
     * @param errcode 业务错误码，成功通常为 0 或空
     * @param errmsg 微信错误描述
     */
    private record AccessTokenResponse(
            @JsonProperty("access_token")
            String accessToken,

            @JsonProperty("expires_in")
            Integer expiresIn,

            Integer errcode,

            String errmsg
    ) {
    }

    /**
     * 微信手机号授权接口请求体。
     *
     * @param code 小程序端取得的一次性手机号授权 code
     */
    private record PhoneNumberRequest(String code) {
    }

    /**
     * 微信手机号授权接口响应。
     *
     * @param errcode 微信业务错误码
     * @param errmsg 微信错误描述
     * @param phoneInfo 成功时的手机号信息
     */
    private record PhoneNumberResponse(
            Integer errcode,

            String errmsg,

            @JsonProperty("phone_info")
            PhoneInfo phoneInfo
    ) {
    }

    /**
     * 微信返回的手机号信息。
     *
     * @param phoneNumber 带区号语义的完整手机号，当前登录流程使用该字段
     * @param purePhoneNumber 不带区号的纯手机号
     * @param countryCode 国家或地区代码
     */
    private record PhoneInfo(
            @JsonProperty("phoneNumber")
            String phoneNumber,

            @JsonProperty("purePhoneNumber")
            String purePhoneNumber,

            @JsonProperty("countryCode")
            String countryCode
    ) {
    }
}
