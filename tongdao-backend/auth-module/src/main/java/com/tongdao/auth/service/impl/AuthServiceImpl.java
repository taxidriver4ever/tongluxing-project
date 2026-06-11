package com.tongdao.auth.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.tongdao.auth.dto.LoginRequest;
import com.tongdao.auth.dto.RefreshTokenRequest;
import com.tongdao.auth.dto.SmsCodeRequest;
import com.tongdao.auth.entity.AuthAccount;
import com.tongdao.auth.mapper.AuthAccountMapper;
import com.tongdao.auth.mapper.AuthLoginLogMapper;
import com.tongdao.auth.mapper.AuthSmsLogMapper;
import com.tongdao.auth.security.AuthPrincipal;
import com.tongdao.auth.security.TokenStore;
import com.tongdao.auth.service.AuthService;
import com.tongdao.auth.vo.CurrentUserResponse;
import com.tongdao.auth.vo.LoginResponse;
import com.tongdao.auth.vo.LogoutResponse;
import com.tongdao.auth.vo.RefreshTokenResponse;
import com.tongdao.auth.vo.SmsCodeResponse;
import com.tongdao.common.exception.BusinessException;
import com.tongdao.common.result.ResultCode;
import com.tongdao.common.utils.SnowflakeIdGenerator;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String DEFAULT_SCENE = "login";
    private static final int SMS_EXPIRE_SECONDS = 300;
    private static final int SMS_COOLDOWN_SECONDS = 60;
    private static final int SMS_DAILY_LIMIT = 10;
    private static final int LOGIN_FAIL_LIMIT = 5;

    private static final String SMS_CODE_KEY = "auth:sms:code:%s:%s";
    private static final String SMS_COOLDOWN_KEY = "auth:sms:cooldown:%s:%s";
    private static final String SMS_DAILY_KEY = "auth:sms:daily:%s:%s";
    private static final String LOGIN_FAIL_KEY = "auth:login:fail:%s";

    private final StringRedisTemplate redisTemplate;
    private final TokenStore tokenStore;
    private final AuthAccountMapper accountMapper;
    private final AuthSmsLogMapper smsLogMapper;
    private final AuthLoginLogMapper loginLogMapper;

    @Override
    public SmsCodeResponse sendSmsCode(SmsCodeRequest request) {
        String scene = normalizeScene(request.scene());
        String phone = request.phone();

        if (Boolean.TRUE.equals(redisTemplate.hasKey(key(SMS_COOLDOWN_KEY, scene, phone)))) {
            throw new BusinessException("发送太频繁，请稍后再试");
        }

        Long dailyCount = redisTemplate.opsForValue().increment(key(SMS_DAILY_KEY, scene, phone));
        if (dailyCount != null && dailyCount == 1) {
            redisTemplate.expire(key(SMS_DAILY_KEY, scene, phone), Duration.ofHours(24));
        }
        if (dailyCount != null && dailyCount > SMS_DAILY_LIMIT) {
            insertSmsLog(phone, scene, false, "mock", "daily limit exceeded");
            throw new BusinessException("发送太频繁，请稍后再试");
        }

        // V1 mock SMS provider. The code is persisted only in Redis and never returned to client.
        String code = generateCode();
        redisTemplate.opsForValue().set(key(SMS_CODE_KEY, scene, phone), code, Duration.ofSeconds(SMS_EXPIRE_SECONDS));
        redisTemplate.opsForValue().set(key(SMS_COOLDOWN_KEY, scene, phone), "1", Duration.ofSeconds(SMS_COOLDOWN_SECONDS));
        insertSmsLog(phone, scene, true, "mock", null);
        return new SmsCodeResponse(SMS_EXPIRE_SECONDS);
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String phone = request.phone();
        validateLoginFailLimit(phone);
        validateSmsCode(phone, request.code());

        AuthAccount account = accountMapper.findByPhone(phone);
        boolean isNewUser = account == null;
        if (account == null) {
            account = createAccount(phone, currentIp());
        }
        if (Integer.valueOf(2).equals(account.getAccountStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        updateLastLogin(account.getUserId(), currentIp());
        redisTemplate.delete(loginFailKey(phone));
        redisTemplate.delete(key(SMS_CODE_KEY, DEFAULT_SCENE, phone));

        TokenStore.TokenPair tokenPair = tokenStore.create(account.getUserId(), phone, request.deviceId());
        insertLoginLog(account.getUserId(), phone, "login", request.deviceId(), currentIp(), true, "login success");
        return new LoginResponse(
                tokenPair.token(),
                tokenPair.refreshToken(),
                account.getUserId(),
                isNewUser,
                tokenPair.expireSeconds()
        );
    }

    @Override
    public LogoutResponse logout(String authorization) {
        AuthPrincipal principal = resolvePrincipal(authorization);
        tokenStore.deleteToken(principal);
        insertLoginLog(principal.userId(), principal.phone(), "logout", principal.deviceId(), currentIp(), true, "logout success");
        return new LogoutResponse(true);
    }

    @Override
    public CurrentUserResponse getCurrentUser(String authorization) {
        AuthPrincipal principal = resolvePrincipal(authorization);
        AuthAccount account = accountMapper.findByUserId(principal.userId());
        if (account == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已失效");
        }
        return new CurrentUserResponse(account.getUserId(), maskPhone(account.getPhone()), "LOGIN");
    }

    @Override
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        Long userId = tokenStore.resolveRefreshToken(request.refreshToken())
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌已失效，请重新登录"));
        AuthAccount account = accountMapper.findByUserId(userId);
        if (account == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已失效");
        }

        tokenStore.deleteUserTokens(userId);
        tokenStore.deleteRefreshToken(request.refreshToken());
        TokenStore.TokenPair tokenPair = tokenStore.create(userId, account.getPhone(), "");
        insertLoginLog(userId, account.getPhone(), "refresh", null, currentIp(), true, "refresh token success");
        return new RefreshTokenResponse(tokenPair.token(), tokenPair.expireSeconds());
    }

    private void validateSmsCode(String phone, String code) {
        String redisKey = key(SMS_CODE_KEY, DEFAULT_SCENE, phone);
        String savedCode = redisTemplate.opsForValue().get(redisKey);
        if (!StringUtils.hasText(savedCode)) {
            increaseLoginFail(phone);
            insertLoginLog(null, phone, "login", null, currentIp(), false, "sms code expired");
            throw new BusinessException("验证码已过期，请重新获取");
        }
        if (!savedCode.equals(code)) {
            increaseLoginFail(phone);
            insertLoginLog(null, phone, "login", null, currentIp(), false, "sms code invalid");
            throw new BusinessException("验证码错误，请重新输入");
        }
    }

    private AuthAccount createAccount(String phone, String ip) {
        LocalDateTime now = LocalDateTime.now();
        AuthAccount account = new AuthAccount();
        account.setId(SnowflakeIdGenerator.nextId());
        account.setUserId(SnowflakeIdGenerator.nextId());
        account.setPhone(phone);
        account.setLastLoginIp(ip);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        accountMapper.insert(account);
        return accountMapper.findByPhone(phone);
    }

    private void updateLastLogin(Long userId, String ip) {
        LocalDateTime now = LocalDateTime.now();
        accountMapper.updateLastLogin(userId, now, ip, now);
    }

    private void insertSmsLog(String phone, String scene, boolean success, String provider, String errorMessage) {
        smsLogMapper.insert(SnowflakeIdGenerator.nextId(), phone, scene, success ? 1 : 2, provider, errorMessage);
    }

    private void insertLoginLog(Long userId, String phone, String actionType, String deviceId, String ip, boolean success, String message) {
        loginLogMapper.insert(SnowflakeIdGenerator.nextId(), userId, phone, actionType, deviceId, ip, success ? 1 : 0, message);
    }

    private void validateLoginFailLimit(String phone) {
        String failCount = redisTemplate.opsForValue().get(loginFailKey(phone));
        if (StringUtils.hasText(failCount) && Integer.parseInt(failCount) >= LOGIN_FAIL_LIMIT) {
            throw new BusinessException("验证码错误次数过多，请稍后再试");
        }
    }

    private void increaseLoginFail(String phone) {
        Long count = redisTemplate.opsForValue().increment(loginFailKey(phone));
        if (count != null && count == 1) {
            redisTemplate.expire(loginFailKey(phone), Duration.ofMinutes(15));
        }
    }

    private AuthPrincipal resolvePrincipal(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        String token = authorization.substring("Bearer ".length());
        return tokenStore.resolve(token)
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已过期"));
    }

    private String normalizeScene(String scene) {
        return StringUtils.hasText(scene) ? scene : DEFAULT_SCENE;
    }

    private String key(String pattern, String scene, String phone) {
        return pattern.formatted(scene, phone);
    }

    private String loginFailKey(String phone) {
        return LOGIN_FAIL_KEY.formatted(phone);
    }

    private String generateCode() {
        return "829416";
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private String currentIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "";
        }
        HttpServletRequest request = attributes.getRequest();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
