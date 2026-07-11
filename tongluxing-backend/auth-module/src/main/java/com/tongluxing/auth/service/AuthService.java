package com.tongluxing.auth.service;

import com.tongluxing.auth.dto.LoginRequest;
import com.tongluxing.auth.dto.AppBindByMiniTicketRequest;
import com.tongluxing.auth.dto.AppLoginRequest;
import com.tongluxing.auth.dto.RefreshTokenRequest;
import com.tongluxing.auth.dto.SmsCodeRequest;
import com.tongluxing.auth.dto.WxPhoneLoginRequest;
import com.tongluxing.auth.vo.CurrentUserResponse;
import com.tongluxing.auth.vo.AppBindTicketResponse;
import com.tongluxing.auth.vo.LoginResponse;
import com.tongluxing.auth.vo.LogoutResponse;
import com.tongluxing.auth.vo.RefreshTokenResponse;
import com.tongluxing.auth.vo.SmsCodeResponse;

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

    /** App 驾驶端手机号验证码登录。 */
    LoginResponse appLogin(AppLoginRequest request);

    /** 小程序端为当前用户创建 App 绑定 ticket。 */
    AppBindTicketResponse createAppBindTicket(String authorization);

    /** App 使用小程序 ticket 绑定并登录。 */
    LoginResponse bindAppByMiniTicket(AppBindByMiniTicketRequest request);

    /** 退出登录并清理登录态。 */
    LogoutResponse logout(String authorization);

    /** 查询当前登录用户。 */
    CurrentUserResponse getCurrentUser(String authorization);

    /** 刷新 access token。 */
    RefreshTokenResponse refreshToken(RefreshTokenRequest request);
}
