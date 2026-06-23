package com.tongdao.auth.service;

import com.tongdao.auth.dto.LoginRequest;
import com.tongdao.auth.dto.RefreshTokenRequest;
import com.tongdao.auth.dto.SmsCodeRequest;
import com.tongdao.auth.dto.WxPhoneLoginRequest;
import com.tongdao.auth.vo.CurrentUserResponse;
import com.tongdao.auth.vo.LoginResponse;
import com.tongdao.auth.vo.LogoutResponse;
import com.tongdao.auth.vo.RefreshTokenResponse;
import com.tongdao.auth.vo.SmsCodeResponse;

/**
 * 认证业务服务接口。
 *
 * <p>向 Controller 暴露稳定的认证用例，隐藏短信、账号、Token 和日志的实现细节。</p>
 */
public interface AuthService {

    /** 发送短信验证码。 */
    SmsCodeResponse sendSmsCode(SmsCodeRequest request);

    /** 手机号验证码登录。 */
    LoginResponse login(LoginRequest request);

    /** 微信小程序手机号授权登录。 */
    LoginResponse wxPhoneLogin(WxPhoneLoginRequest request);

    /** 退出登录并清理登录态。 */
    LogoutResponse logout(String authorization);

    /** 查询当前登录用户。 */
    CurrentUserResponse getCurrentUser(String authorization);

    /** 刷新 access token。 */
    RefreshTokenResponse refreshToken(RefreshTokenRequest request);
}
