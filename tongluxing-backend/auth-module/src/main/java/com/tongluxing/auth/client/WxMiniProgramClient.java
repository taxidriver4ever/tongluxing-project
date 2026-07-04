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
        String accessToken = getAccessToken();
        try {
            PhoneNumberResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/wxa/business/getuserphonenumber")
                            .queryParam("access_token", accessToken)
                            .build())
                    .body(new PhoneNumberRequest(code))
                    .retrieve()
                    .body(PhoneNumberResponse.class);

            if (response == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信手机号授权失败");
            }
            if (response.errcode() != null && response.errcode() != 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信手机号授权失败：" + response.errmsg());
            }
            if (response.phoneInfo() == null || !StringUtils.hasText(response.phoneInfo().phoneNumber())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信未返回手机号");
            }
            return response.phoneInfo().phoneNumber();
        } catch (RestClientException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "微信手机号授权失败", exception);
        }
    }

    /**
     * 获取微信接口调用凭证。
     *
     * <p>这里保持简单直连微信；如果后续调用量变大，可以增加 Redis 缓存 access_token。</p>
     */
    private String getAccessToken() {
        if (!StringUtils.hasText(properties.getAppId()) || !StringUtils.hasText(properties.getAppSecret())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "微信小程序 appId 或 appSecret 未配置");
        }

        try {
            AccessTokenResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/cgi-bin/token")
                            .queryParam("grant_type", "client_credential")
                            .queryParam("appid", properties.getAppId())
                            .queryParam("secret", properties.getAppSecret())
                            .build())
                    .retrieve()
                    .body(AccessTokenResponse.class);

            if (response == null) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信 access_token 获取失败");
            }
            if (response.errcode() != null && response.errcode() != 0) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信 access_token 获取失败：" + response.errmsg());
            }
            if (!StringUtils.hasText(response.accessToken())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信 access_token 为空");
            }
            return response.accessToken();
        } catch (RestClientException exception) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "微信 access_token 获取失败", exception);
        }
    }

    /** 微信获取 access_token 接口响应。 */
    private record AccessTokenResponse(
            @JsonProperty("access_token")
            String accessToken,

            @JsonProperty("expires_in")
            Integer expiresIn,

            Integer errcode,

            String errmsg
    ) {
    }

    /** 微信手机号授权接口请求体。 */
    private record PhoneNumberRequest(String code) {
    }

    /** 微信手机号授权接口响应。 */
    private record PhoneNumberResponse(
            Integer errcode,

            String errmsg,

            @JsonProperty("phone_info")
            PhoneInfo phoneInfo
    ) {
    }

    /** 微信返回的手机号信息。 */
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
