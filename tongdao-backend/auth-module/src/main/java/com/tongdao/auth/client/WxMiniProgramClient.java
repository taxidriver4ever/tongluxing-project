package com.tongdao.auth.client;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tongdao.auth.config.WxMiniProgramProperties;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class WxMiniProgramClient {

    private static final String WECHAT_API_BASE_URL = "https://api.weixin.qq.com";

    private final WxMiniProgramProperties properties;
    private final RestClient restClient = RestClient.create(WECHAT_API_BASE_URL);

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

    private record AccessTokenResponse(
            @JsonProperty("access_token")
            String accessToken,

            @JsonProperty("expires_in")
            Integer expiresIn,

            Integer errcode,

            String errmsg
    ) {
    }

    private record PhoneNumberRequest(String code) {
    }

    private record PhoneNumberResponse(
            Integer errcode,

            String errmsg,

            @JsonProperty("phone_info")
            PhoneInfo phoneInfo
    ) {
    }

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
