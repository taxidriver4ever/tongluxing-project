package com.tongluxing.auth.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.tongluxing.auth.client.WxMiniProgramClient;
import com.tongluxing.auth.dto.AppBindByMiniTicketRequest;
import com.tongluxing.auth.dto.AppLoginRequest;
import com.tongluxing.auth.dto.LoginRequest;
import com.tongluxing.auth.dto.PasswordLoginRequest;
import com.tongluxing.auth.dto.RefreshTokenRequest;
import com.tongluxing.auth.dto.RegisterSource;
import com.tongluxing.auth.dto.SmsCodeRequest;
import com.tongluxing.auth.dto.SetPasswordRequest;
import com.tongluxing.auth.dto.WxPhoneLoginRequest;
import com.tongluxing.auth.entity.AuthAccount;
import com.tongluxing.auth.entity.AuthDeviceBinding;
import com.tongluxing.auth.entity.AuthPasswordCredential;
import com.tongluxing.auth.mapper.AuthAccountMapper;
import com.tongluxing.auth.mapper.AuthDeviceBindingMapper;
import com.tongluxing.auth.mapper.AuthLoginLogMapper;
import com.tongluxing.auth.mapper.AuthPasswordCredentialMapper;
import com.tongluxing.auth.mapper.AuthSmsLogMapper;
import com.tongluxing.auth.security.AuthPrincipal;
import com.tongluxing.auth.security.AuthSecurityExceptionHandler;
import com.tongluxing.auth.security.TokenStore;
import com.tongluxing.auth.service.AuthService;
import com.tongluxing.auth.vo.CurrentUserResponse;
import com.tongluxing.auth.vo.AppBindTicketResponse;
import com.tongluxing.auth.vo.LoginResponse;
import com.tongluxing.auth.vo.LogoutResponse;
import com.tongluxing.auth.vo.RefreshTokenResponse;
import com.tongluxing.auth.vo.SmsCodeResponse;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.event.UserRegisteredEvent;
import com.tongluxing.common.result.ResultCode;
import com.tongluxing.common.utils.SnowflakeIdGenerator;

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
    /** 小程序生成的 App 绑定 ticket。 */
    private static final String APP_BIND_TICKET_KEY = "auth:app:bind-ticket:%s";
    private static final int APP_BIND_TICKET_EXPIRE_SECONDS = 300;
    private static final String CLIENT_MINI_PROGRAM = "MINI_PROGRAM";
    private static final String CLIENT_APP_DRIVER = "APP_DRIVER";
    private static final String CLIENT_MERCHANT_WEB = "MERCHANT_WEB";
    /** 所有客户端共用账号级会话，保证一个账号同时只能保留一个设备登录。 */
    private static final String SESSION_SCOPE_ACCOUNT = "ACCOUNT";
    private static final String PASSWORD_VERSION_BCRYPT = "BCRYPT";

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
    /** 设备绑定 Mapper。 */
    private final AuthDeviceBindingMapper deviceBindingMapper;
    /** 密码凭证 Mapper。 */
    private final AuthPasswordCredentialMapper passwordCredentialMapper;
    /** 密码哈希器。 */
    private final PasswordEncoder passwordEncoder;
    /** 微信小程序接口客户端。 */
    private final WxMiniProgramClient wxMiniProgramClient;
    /** Spring 事件发布器，用于首次注册成功后通知业务模块处理来源绑定。 */
    private final ApplicationEventPublisher eventPublisher;

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

        LoginResponse response = doLoginByPhone(phone, request.deviceId(), null, null, CLIENT_MINI_PROGRAM, "login", request.password(), request.registerSource());
        redisTemplate.delete(key(SMS_CODE_KEY, DEFAULT_SCENE, phone));
        return response;
    }

    /** 手机号密码登录。 */
    @Override
    @Transactional
    public LoginResponse passwordLogin(PasswordLoginRequest request) {
        String phone = request.phone();
        validateLoginFailLimit(phone);
        AuthAccount account = accountMapper.findByPhone(phone);
        if (account == null) {
            increaseLoginFail(phone);
            insertLoginLog(null, phone, "password_login", request.deviceId(), currentIp(), false, "account not found");
            throw new BusinessException(ResultCode.UNAUTHORIZED, "手机号或密码错误");
        }
        if (Integer.valueOf(2).equals(account.getAccountStatus())) {
            insertLoginLog(account.getUserId(), phone, "password_login", request.deviceId(), currentIp(), false, "account disabled");
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        AuthPasswordCredential credential = passwordCredentialMapper.findByUserId(account.getUserId());
        if (credential == null || !Integer.valueOf(1).equals(credential.getPasswordStatus())) {
            insertLoginLog(account.getUserId(), phone, "password_login", request.deviceId(), currentIp(), false, "password credential missing");
            throw new BusinessException(ResultCode.BAD_REQUEST, "账号未设置密码，请先使用验证码登录设置密码");
        }
        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            increaseLoginFail(phone);
            insertLoginLog(account.getUserId(), phone, "password_login", request.deviceId(), currentIp(), false, "password invalid");
            throw new BusinessException(ResultCode.UNAUTHORIZED, "手机号或密码错误");
        }

        String clientType = passwordClientType(request.clientType());
        String ip = currentIp();
        updateLastLogin(account.getUserId(), ip);
        upsertDeviceBinding(account, clientType, request.deviceId(), null, null, ip);
        redisTemplate.delete(loginFailKey(phone));

        TokenStore.TokenPair tokenPair = tokenStore.create(
                account.getUserId(), phone, normalizeDeviceId(request.deviceId()), SESSION_SCOPE_ACCOUNT);
        insertLoginLog(account.getUserId(), phone, "password_login", request.deviceId(), ip, true, "password login success");
        return new LoginResponse(
                tokenPair.token(),
                tokenPair.refreshToken(),
                account.getUserId(),
                false,
                true,
                tokenPair.expireSeconds()
        );
    }

    /** 微信手机号授权登录。 */
    @Override
    @Transactional
    public LoginResponse wxPhoneLogin(WxPhoneLoginRequest request) {
        String phone;
        if (StringUtils.hasText(request.mockPhone())) {
            phone = request.mockPhone().trim();
        } else {
            if (!StringUtils.hasText(request.code())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信授权 code 或 mockPhone 至少传一个");
            }
            phone = wxMiniProgramClient.getPhoneNumber(request.code());
        }
        RegisterSource source = request.registerSource();
        if (source == null && StringUtils.hasText(request.inviteCode())) {
            source = new RegisterSource("INVITE", request.inviteCode().trim());
        }
        return doLoginByPhone(phone, request.deviceId(), null, null, CLIENT_MINI_PROGRAM,
                "wx_phone_login", request.password(), source);
    }

    /** 当前登录用户首次设置密码；重复设置不会覆盖旧密码。 */
    @Override
    @Transactional
    public void setPassword(String authorization, SetPasswordRequest request) {
        AuthPrincipal principal = resolvePrincipal(authorization);
        validateSetPassword(request.password());
        AuthPasswordCredential existing = passwordCredentialMapper.findByUserId(principal.userId());
        if (existing != null) {
            if (passwordEncoder.matches(request.password(), existing.getPasswordHash())) {
                return;
            }
            throw new BusinessException(409, "账号已经设置密码");
        }
        createPasswordCredential(principal.userId(), request.password());
    }

    /** App 驾驶端手机号验证码登录。 */
    @Override
    @Transactional
    public LoginResponse appLogin(AppLoginRequest request) {
        String phone = request.phone();
        validateLoginFailLimit(phone);
        validateSmsCode(phone, request.code());

        LoginResponse response = doLoginByPhone(
                phone,
                request.deviceId(),
                request.deviceName(),
                request.platform(),
                CLIENT_APP_DRIVER,
                "app_login",
                request.password(),
                request.registerSource()
        );
        redisTemplate.delete(key(SMS_CODE_KEY, DEFAULT_SCENE, phone));
        return response;
    }

    /** 小程序端为当前登录用户生成一次性 App 绑定 ticket。 */
    @Override
    public AppBindTicketResponse createAppBindTicket(String authorization) {
        AuthPrincipal principal = resolvePrincipal(authorization);
        String ticket = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                APP_BIND_TICKET_KEY.formatted(ticket),
                String.valueOf(principal.userId()),
                Duration.ofSeconds(APP_BIND_TICKET_EXPIRE_SECONDS)
        );
        return new AppBindTicketResponse(ticket, APP_BIND_TICKET_EXPIRE_SECONDS);
    }

    /** App 使用小程序 ticket 绑定并登录。 */
    @Override
    @Transactional
    public LoginResponse bindAppByMiniTicket(AppBindByMiniTicketRequest request) {
        String key = APP_BIND_TICKET_KEY.formatted(request.ticket());
        String userIdText = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(userIdText)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "绑定 ticket 已失效");
        }
        redisTemplate.delete(key);

        AuthAccount account = accountMapper.findByUserId(Long.parseLong(userIdText));
        if (account == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已失效");
        }
        if (Integer.valueOf(2).equals(account.getAccountStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        String ip = currentIp();
        updateLastLogin(account.getUserId(), ip);
        upsertDeviceBinding(account, CLIENT_APP_DRIVER, request.deviceId(), request.deviceName(), request.platform(), ip);
        TokenStore.TokenPair tokenPair = tokenStore.create(
                account.getUserId(), account.getPhone(), normalizeDeviceId(request.deviceId()), SESSION_SCOPE_ACCOUNT);
        insertLoginLog(account.getUserId(), account.getPhone(), "app_bind_login", request.deviceId(), ip, true, "app bind by mini ticket success");
        return new LoginResponse(
                tokenPair.token(),
                tokenPair.refreshToken(),
                account.getUserId(),
                false,
                hasPasswordCredential(account.getUserId()),
                tokenPair.expireSeconds()
        );
    }

    /**
     * 手机号登录公共流程。
     *
     * <p>手机号验证码登录和微信手机号登录最终都会走到这里：查账号、必要时创建账号、签发 Token、记录日志。</p>
     */
    private LoginResponse doLoginByPhone(String phone, String deviceId, String deviceName, String platform,
                                         String clientType, String actionType, String password, RegisterSource registerSource) {
        AuthAccount account = accountMapper.findByPhone(phone);
        boolean isNewUser = account == null;
        if (account == null) {
            account = createAccount(phone, currentIp());
            if (StringUtils.hasText(password)) {
                validateInitialPassword(password);
                createPasswordCredential(account.getUserId(), password);
            }
            publishUserRegistered(account, registerSource);
        }
        if (Integer.valueOf(2).equals(account.getAccountStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        boolean passwordSet = hasPasswordCredential(account.getUserId());

        String ip = currentIp();
        updateLastLogin(account.getUserId(), ip);
        upsertDeviceBinding(account, clientType, deviceId, deviceName, platform, ip);
        // 登录成功后清除验证码错误计数，避免用户后续被旧失败次数影响。
        redisTemplate.delete(loginFailKey(phone));

        TokenStore.TokenPair tokenPair = tokenStore.create(
                account.getUserId(), phone, normalizeDeviceId(deviceId), sessionScope(clientType));
        insertLoginLog(account.getUserId(), phone, actionType, deviceId, ip, true, actionType + " success");
        return new LoginResponse(
                tokenPair.token(),
                tokenPair.refreshToken(),
                account.getUserId(),
                isNewUser,
                passwordSet,
                tokenPair.expireSeconds()
        );
    }


    /** 密码登录只允许 App 与商家 Web；两者共享同一个单点登录会话域。 */
    private String passwordClientType(String value) {
        if (!StringUtils.hasText(value)) return CLIENT_APP_DRIVER;
        String normalized = value.trim().toUpperCase();
        if (!Set.of(CLIENT_APP_DRIVER, CLIENT_MERCHANT_WEB).contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "客户端类型不正确");
        }
        return normalized;
    }

    private String sessionScope(String clientType) {
        return SESSION_SCOPE_ACCOUNT;
    }

    /** 首次注册时校验初始密码。 */
    private void validateInitialPassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "首次注册需要设置密码");
        }
        if (!password.equals(password.trim())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码不能包含首尾空格");
        }
        if (password.length() < 8 || password.length() > 32) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码长度需为8-32位");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码需至少包含字母和数字");
        }
    }

    /** 联调初始化密码规则；允许需求示例中的纯数字六位密码。 */
    private void validateSetPassword(String password) {
        if (!StringUtils.hasText(password) || !password.equals(password.trim())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码不能为空且不能包含首尾空格");
        }
        if (password.length() < 6 || password.length() > 32) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码长度需为6-32位");
        }
    }

    /** 创建密码凭证，只保存不可逆哈希。 */
    private void createPasswordCredential(Long userId, String password) {
        LocalDateTime now = LocalDateTime.now();
        AuthPasswordCredential credential = new AuthPasswordCredential();
        credential.setId(SnowflakeIdGenerator.nextId());
        credential.setUserId(userId);
        credential.setPasswordHash(passwordEncoder.encode(password));
        credential.setPasswordVersion(PASSWORD_VERSION_BCRYPT);
        credential.setPasswordStatus(1);
        credential.setLastSetTime(now);
        credential.setCreatedAt(now);
        credential.setUpdatedAt(now);
        credential.setDeleted(0);
        passwordCredentialMapper.insert(credential);
    }

    /** 是否已有密码凭证。 */
    private boolean hasPasswordCredential(Long userId) {
        return passwordCredentialMapper.findByUserId(userId) != null;
    }

    /** 新增或刷新设备绑定关系。 */
    private void upsertDeviceBinding(AuthAccount account, String clientType, String deviceId, String deviceName, String platform, String ip) {
        String normalizedDeviceId = StringUtils.hasText(deviceId) ? deviceId : "default";
        LocalDateTime now = LocalDateTime.now();
        int rows = deviceBindingMapper.updateLogin(
                account.getUserId(),
                account.getPhone(),
                clientType,
                normalizedDeviceId,
                normalize(deviceName),
                normalize(platform),
                now,
                ip,
                now
        );
        if (rows > 0) {
            return;
        }
        AuthDeviceBinding binding = new AuthDeviceBinding();
        binding.setId(SnowflakeIdGenerator.nextId());
        binding.setUserId(account.getUserId());
        binding.setPhone(account.getPhone());
        binding.setClientType(clientType);
        binding.setDeviceId(normalizedDeviceId);
        binding.setDeviceName(normalize(deviceName));
        binding.setPlatform(normalize(platform));
        binding.setBindStatus(1);
        binding.setLastLoginTime(now);
        binding.setLastLoginIp(ip);
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);
        binding.setDeleted(0);
        deviceBindingMapper.insert(binding);
    }

    /** 首次注册成功后发布注册来源事件；已有用户登录不会调用该方法。 */
    private void publishUserRegistered(AuthAccount account, RegisterSource registerSource) {
        String sourceType = registerSource == null ? null : registerSource.sourceType();
        String sourceCode = registerSource == null ? null : registerSource.sourceCode();
        eventPublisher.publishEvent(new UserRegisteredEvent(
                account.getUserId(),
                sourceType,
                sourceCode,
                account.getCreatedAt()
        ));
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
        TokenStore.RefreshResolution resolution = tokenStore.resolveRefresh(request.refreshToken());
        if (resolution.status() == TokenStore.RefreshStatus.KICKED) {
            throw new BusinessException(
                    AuthSecurityExceptionHandler.ACCOUNT_LOGGED_IN_ELSEWHERE_CODE,
                    AuthSecurityExceptionHandler.ACCOUNT_LOGGED_IN_ELSEWHERE_MESSAGE);
        }
        if (resolution.status() != TokenStore.RefreshStatus.VALID) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌已失效，请重新登录");
        }
        TokenStore.RefreshPrincipal refreshPrincipal = resolution.principal();
        AuthAccount account = accountMapper.findByUserId(refreshPrincipal.userId());
        if (account == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已失效");
        }

        // refresh token 一次性使用：刷新成功后立即删除旧 refresh token。
        tokenStore.deleteRefreshToken(request.refreshToken());
        TokenStore.TokenPair tokenPair = tokenStore.create(
                refreshPrincipal.userId(), account.getPhone(), refreshPrincipal.deviceId(), refreshPrincipal.sessionScope());
        insertLoginLog(refreshPrincipal.userId(), account.getPhone(), "refresh", null, currentIp(), true, "refresh token success");
        return new RefreshTokenResponse(
                tokenPair.token(),
                tokenPair.refreshToken(),
                tokenPair.expireSeconds()
        );
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
            throw new BusinessException("登录失败次数过多，请稍后再试");
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

    /** 规范化普通文本。 */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    /** 规范化设备 ID，保证 Token 和设备绑定表使用同一维度。 */
    private String normalizeDeviceId(String deviceId) {
        return StringUtils.hasText(deviceId) ? deviceId.trim() : "default";
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
