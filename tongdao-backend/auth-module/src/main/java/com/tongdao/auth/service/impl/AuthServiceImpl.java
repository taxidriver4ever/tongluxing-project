package com.tongdao.auth.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.tongdao.auth.client.WxMiniProgramClient;
import com.tongdao.auth.dto.LoginRequest;
import com.tongdao.auth.dto.RefreshTokenRequest;
import com.tongdao.auth.dto.SmsCodeRequest;
import com.tongdao.auth.dto.WxPhoneLoginRequest;
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

/**
 * 认证业务服务实现。
 *
 * <p>负责短信验证码登录、微信手机号登录、Token 签发/刷新/退出和认证日志记录。</p>
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 默认验证码场景：登录。 */
    private static final String DEFAULT_SCENE = "login";
    /** 验证码有效期，单位秒。 */
    private static final int SMS_EXPIRE_SECONDS = 300;
    /** 同一手机号同一场景发送验证码冷却时间，单位秒。 */
    private static final int SMS_COOLDOWN_SECONDS = 60;
    /** 同一手机号同一场景每日最多发送次数。 */
    private static final int SMS_DAILY_LIMIT = 10;
    /** 登录验证码错误次数上限。 */
    private static final int LOGIN_FAIL_LIMIT = 5;

    /** 短信验证码 Redis key。 */
    private static final String SMS_CODE_KEY = "auth:sms:code:%s:%s";
    /** 短信验证码发送冷却 Redis key。 */
    private static final String SMS_COOLDOWN_KEY = "auth:sms:cooldown:%s:%s";
    /** 短信验证码每日发送次数 Redis key。 */
    private static final String SMS_DAILY_KEY = "auth:sms:daily:%s:%s";
    /** 登录失败次数 Redis key。 */
    private static final String LOGIN_FAIL_KEY = "auth:login:fail:%s";

    /** Redis 模板，用于验证码、限流计数和登录失败计数。 */
    private final StringRedisTemplate redisTemplate;
    /** Token 存储组件，负责 JWT 签发和 Redis 登录态维护。 */
    private final TokenStore tokenStore;
    /** 认证账号表 Mapper。 */
    private final AuthAccountMapper accountMapper;
    /** 短信发送日志 Mapper。 */
    private final AuthSmsLogMapper smsLogMapper;
    /** 登录行为日志 Mapper。 */
    private final AuthLoginLogMapper loginLogMapper;
    /** 微信小程序接口客户端。 */
    private final WxMiniProgramClient wxMiniProgramClient;

    /** 发送登录验证码，并执行发送冷却和每日次数限制。 */
    @Override
    public SmsCodeResponse sendSmsCode(SmsCodeRequest request) {
        String scene = normalizeScene(request.scene());
        String phone = request.phone();

        // 冷却窗口内不允许重复发送，避免前端连点或短信轰炸。
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key(SMS_COOLDOWN_KEY, scene, phone)))) {
            throw new BusinessException("发送太频繁，请稍后再试");
        }

        // 每日次数限制按“场景 + 手机号”维度统计，首次写入时设置 24 小时过期。
        Long dailyCount = redisTemplate.opsForValue().increment(key(SMS_DAILY_KEY, scene, phone));
        if (dailyCount != null && dailyCount == 1) {
            redisTemplate.expire(key(SMS_DAILY_KEY, scene, phone), Duration.ofHours(24));
        }
        if (dailyCount != null && dailyCount > SMS_DAILY_LIMIT) {
            insertSmsLog(phone, scene, false, "mock", "daily limit exceeded");
            throw new BusinessException("发送太频繁，请稍后再试");
        }

        // V1 阶段使用 mock 短信通道；验证码只写 Redis，不返回给客户端。
        String code = generateCode();
        redisTemplate.opsForValue().set(key(SMS_CODE_KEY, scene, phone), code, Duration.ofSeconds(SMS_EXPIRE_SECONDS));
        redisTemplate.opsForValue().set(key(SMS_COOLDOWN_KEY, scene, phone), "1", Duration.ofSeconds(SMS_COOLDOWN_SECONDS));
        insertSmsLog(phone, scene, true, "mock", null);
        return new SmsCodeResponse(SMS_EXPIRE_SECONDS);
    }

    /** 手机号验证码登录。 */
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String phone = request.phone();
        validateLoginFailLimit(phone);
        validateSmsCode(phone, request.code());

        LoginResponse response = doLoginByPhone(phone, request.deviceId(), "login");
        redisTemplate.delete(key(SMS_CODE_KEY, DEFAULT_SCENE, phone));
        return response;
    }

    /** 微信手机号授权登录。 */
    @Override
    @Transactional
    public LoginResponse wxPhoneLogin(WxPhoneLoginRequest request) {
        String phone = wxMiniProgramClient.getPhoneNumber(request.code());
        return doLoginByPhone(phone, request.deviceId(), "wx_phone_login");
    }

    /**
     * 手机号登录公共流程。
     *
     * <p>手机号验证码登录和微信手机号登录最终都会走到这里：查账号、必要时创建账号、签发 Token、记录日志。</p>
     */
    private LoginResponse doLoginByPhone(String phone, String deviceId, String actionType) {
        AuthAccount account = accountMapper.findByPhone(phone);
        boolean isNewUser = account == null;
        if (account == null) {
            account = createAccount(phone, currentIp());
        }
        if (Integer.valueOf(2).equals(account.getAccountStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        String ip = currentIp();
        updateLastLogin(account.getUserId(), ip);
        // 登录成功后清除验证码错误计数，避免用户后续被旧失败次数影响。
        redisTemplate.delete(loginFailKey(phone));

        TokenStore.TokenPair tokenPair = tokenStore.create(account.getUserId(), phone, deviceId);
        insertLoginLog(account.getUserId(), phone, actionType, deviceId, ip, true, actionType + " success");
        return new LoginResponse(
                tokenPair.token(),
                tokenPair.refreshToken(),
                account.getUserId(),
                isNewUser,
                tokenPair.expireSeconds()
        );
    }

    /** 退出登录：解析当前 Token，删除 Redis 登录态，并记录退出日志。 */
    @Override
    public LogoutResponse logout(String authorization) {
        AuthPrincipal principal = resolvePrincipal(authorization);
        tokenStore.deleteToken(principal);
        insertLoginLog(principal.userId(), principal.phone(), "logout", principal.deviceId(), currentIp(), true, "logout success");
        return new LogoutResponse(true);
    }

    /** 获取当前登录用户基础信息。 */
    @Override
    public CurrentUserResponse getCurrentUser(String authorization) {
        AuthPrincipal principal = resolvePrincipal(authorization);
        AuthAccount account = accountMapper.findByUserId(principal.userId());
        if (account == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已失效");
        }
        return new CurrentUserResponse(account.getUserId(), maskPhone(account.getPhone()), "LOGIN");
    }

    /** 使用 refresh token 换取新的 access token。 */
    @Override
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        TokenStore.RefreshPrincipal refreshPrincipal = tokenStore.resolveRefreshToken(request.refreshToken())
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌已失效，请重新登录"));
        AuthAccount account = accountMapper.findByUserId(refreshPrincipal.userId());
        if (account == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已失效");
        }

        // refresh token 一次性使用：刷新成功后立即删除旧 refresh token。
        tokenStore.deleteRefreshToken(request.refreshToken());
        TokenStore.TokenPair tokenPair = tokenStore.create(refreshPrincipal.userId(), account.getPhone(), "");
        insertLoginLog(refreshPrincipal.userId(), account.getPhone(), "refresh", null, currentIp(), true, "refresh token success");
        return new RefreshTokenResponse(tokenPair.token(), tokenPair.expireSeconds());
    }

    /** 校验短信验证码；过期或错误都会累计登录失败次数。 */
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

    /** 创建认证账号，并分配新的业务用户 ID。 */
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

    /** 更新账号最近登录信息。 */
    private void updateLastLogin(Long userId, String ip) {
        LocalDateTime now = LocalDateTime.now();
        accountMapper.updateLastLogin(userId, now, ip, now);
    }

    /** 写入短信发送日志。 */
    private void insertSmsLog(String phone, String scene, boolean success, String provider, String errorMessage) {
        smsLogMapper.insert(SnowflakeIdGenerator.nextId(), phone, scene, success ? 1 : 2, provider, errorMessage);
    }

    /** 写入登录行为日志。 */
    private void insertLoginLog(Long userId, String phone, String actionType, String deviceId, String ip, boolean success, String message) {
        loginLogMapper.insert(SnowflakeIdGenerator.nextId(), userId, phone, actionType, deviceId, ip, success ? 1 : 0, message);
    }

    /** 登录前检查验证码错误次数是否达到上限。 */
    private void validateLoginFailLimit(String phone) {
        String failCount = redisTemplate.opsForValue().get(loginFailKey(phone));
        if (StringUtils.hasText(failCount) && Integer.parseInt(failCount) >= LOGIN_FAIL_LIMIT) {
            throw new BusinessException("验证码错误次数过多，请稍后再试");
        }
    }

    /** 增加验证码错误次数，首次失败时设置 15 分钟过期。 */
    private void increaseLoginFail(String phone) {
        Long count = redisTemplate.opsForValue().increment(loginFailKey(phone));
        if (count != null && count == 1) {
            redisTemplate.expire(loginFailKey(phone), Duration.ofMinutes(15));
        }
    }

    /** 从 Authorization 请求头解析当前登录主体。 */
    private AuthPrincipal resolvePrincipal(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        String token = authorization.substring("Bearer ".length());
        return tokenStore.resolve(token)
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已过期"));
    }

    /** 规范化短信验证码场景，空值使用默认登录场景。 */
    private String normalizeScene(String scene) {
        return StringUtils.hasText(scene) ? scene : DEFAULT_SCENE;
    }

    /** 按统一格式生成短信相关 Redis key。 */
    private String key(String pattern, String scene, String phone) {
        return pattern.formatted(scene, phone);
    }

    /** 生成登录失败次数 Redis key。 */
    private String loginFailKey(String phone) {
        return LOGIN_FAIL_KEY.formatted(phone);
    }

    /** 生成短信验证码；当前为 mock 固定验证码，方便联调。 */
    private String generateCode() {
        return "829416";
    }

    /** 手机号脱敏展示。 */
    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 获取当前请求 IP，优先读取网关/代理透传的 X-Forwarded-For。 */
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
