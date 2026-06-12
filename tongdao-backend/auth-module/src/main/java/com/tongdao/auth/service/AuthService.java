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

public interface AuthService {

    SmsCodeResponse sendSmsCode(SmsCodeRequest request);

    LoginResponse login(LoginRequest request);

    LoginResponse wxPhoneLogin(WxPhoneLoginRequest request);

    LogoutResponse logout(String authorization);

    CurrentUserResponse getCurrentUser(String authorization);

    RefreshTokenResponse refreshToken(RefreshTokenRequest request);
}
