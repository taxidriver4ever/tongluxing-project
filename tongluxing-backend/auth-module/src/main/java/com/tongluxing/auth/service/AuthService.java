package com.tongluxing.auth.service;

import com.tongluxing.auth.dto.LoginRequest;
import com.tongluxing.auth.dto.AppLoginRequest;
import com.tongluxing.auth.dto.PasswordLoginRequest;
import com.tongluxing.auth.dto.RefreshTokenRequest;
import com.tongluxing.auth.dto.SmsCodeRequest;
import com.tongluxing.auth.dto.SetPasswordRequest;
import com.tongluxing.auth.dto.WxPhoneLoginRequest;
import com.tongluxing.auth.vo.CurrentUserResponse;
import com.tongluxing.auth.vo.LoginResponse;
import com.tongluxing.auth.vo.LogoutResponse;
import com.tongluxing.auth.vo.RefreshTokenResponse;
import com.tongluxing.auth.vo.SmsCodeResponse;

/**
 * 认证业务服务接口。
 *
 * <p>向 Controller 暴露稳定的认证用例，隐藏短信、账号、Token 和日志的实现细节。</p>
 *
 * <p>所有返回登录结果的方法都遵循账号级单点登录：新令牌签发后，该手机号在 App、
 * 小程序和商家 Web 的旧会话都会失效。实现层负责事务、Redis 状态和审计日志，
 * 调用方不得绕过本接口直接组合 Mapper 与 TokenStore。</p>
 */
public interface AuthService {

    /**
     * 发送短信验证码，并执行冷却与每日次数限制。
     *
     * @param request 手机号和验证码场景
     * @return 验证码有效期；不会返回验证码正文
     */
    SmsCodeResponse sendSmsCode(SmsCodeRequest request);

    /**
     * 小程序手机号验证码登录。
     *
     * @return 登录令牌、账号状态和首次登录引导状态
     */
    LoginResponse login(LoginRequest request);

    /**
     * 手机号密码登录；账号不存在或未设置密码时不会自动注册。
     *
     * @param request 手机号、密码、设备和客户端类型
     */
    LoginResponse passwordLogin(PasswordLoginRequest request);

    /**
     * 微信手机号授权登录；正式 code 与联调 mockPhone 最终进入同一账号流程。
     *
     * @param request 微信授权参数和可选注册来源
     */
    LoginResponse wxPhoneLogin(WxPhoneLoginRequest request);

    /**
     * 为当前登录用户创建第一份密码凭证。
     *
     * @param authorization 当前 access token 的完整 Bearer 请求头
     * @param request 待哈希保存的初始密码
     */
    void setPassword(String authorization, SetPasswordRequest request);

    /**
     * 幂等标记小程序邀请码引导已经展示过。
     *
     * @param authorization 当前 access token 的完整 Bearer 请求头
     */
    void completeMiniInviteOnboarding(String authorization);

    /** App 驾驶端手机号验证码登录，并登记 App 设备绑定。 */
    LoginResponse appLogin(AppLoginRequest request);

    /**
     * 退出登录并精确撤销当前 access token。
     *
     * @param authorization 当前 access token 的完整 Bearer 请求头
     */
    LogoutResponse logout(String authorization);

    /**
     * 查询当前登录用户的最小认证资料。
     *
     * @param authorization 当前 access token 的完整 Bearer 请求头
     */
    CurrentUserResponse getCurrentUser(String authorization);

    /**
     * 校验并轮换 refresh token，同时撤销旧令牌对。
     *
     * @param request 当前 refresh token
     * @return 全新的 access/refresh 令牌对
     */
    RefreshTokenResponse refreshToken(RefreshTokenRequest request);
}
