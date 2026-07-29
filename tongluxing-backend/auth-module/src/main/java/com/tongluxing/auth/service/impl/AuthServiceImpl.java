package com.tongluxing.auth.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
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
 *
 * <p>登录主流程按“入口校验 → 查建账号 → 校验账号状态 → 初始化可选密码 →
 * 更新账号与设备审计 → 清理失败计数 → 签发账号级单点令牌 → 写登录日志”执行。
 * 创建账号、密码凭证和设备记录的方法由事务性公开用例调用，异常会回滚数据库写入；
 * Redis 验证码、限流和 Token 状态属于跨请求安全状态，分别设置明确 TTL。</p>
 *
 * <p>安全边界：验证码与明文密码不写日志；密码只保存 BCrypt 哈希；refresh token
 * 成功使用后立即轮换；注册来源只在首次创建账号时通过事件发布，老用户登录不会覆盖首次归因。</p>
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
    /** 微信小程序客户端审计类型。 */
    private static final String CLIENT_MINI_PROGRAM = "MINI_PROGRAM";
    /** 驾驶端 App 客户端审计类型。 */
    private static final String CLIENT_APP_DRIVER = "APP_DRIVER";
    /** 商家 Web 客户端审计类型。 */
    private static final String CLIENT_MERCHANT_WEB = "MERCHANT_WEB";
    /** 所有客户端共用账号级会话，保证一个账号同时只能保留一个设备登录。 */
    private static final String SESSION_SCOPE_ACCOUNT = "ACCOUNT";
    /** 密码凭证算法版本标识，用于未来平滑升级哈希算法。 */
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
        // 先统一场景名称，确保验证码、冷却计数和每日计数使用完全相同的 Redis 维度。
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
            // 超限同样记录失败审计，但不生成验证码，避免绕过发送额度消耗短信资源。
            insertSmsLog(phone, scene, false, "mock", "daily limit exceeded");
            throw new BusinessException("发送太频繁，请稍后再试");
        }

        // V1 阶段使用 mock 短信通道；验证码只写 Redis，不返回给客户端。
        String code = generateCode();
        // 验证码键控制“可校验时间”，冷却键单独控制“再次发送时间”，两种 TTL 不能混用。
        redisTemplate.opsForValue().set(key(SMS_CODE_KEY, scene, phone), code, Duration.ofSeconds(SMS_EXPIRE_SECONDS));
        redisTemplate.opsForValue().set(key(SMS_COOLDOWN_KEY, scene, phone), "1", Duration.ofSeconds(SMS_COOLDOWN_SECONDS));
        // 日志只记录发送结果和供应商，绝不保存验证码正文。
        insertSmsLog(phone, scene, true, "mock", null);
        return new SmsCodeResponse(SMS_EXPIRE_SECONDS);
    }

    /** 手机号验证码登录。 */
    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String phone = request.phone();
        // 在读取验证码前检查累计失败次数，达到上限的手机号直接拒绝继续试错。
        validateLoginFailLimit(phone);
        // 校验失败会递增失败计数并写登录审计；成功时暂不删除，等完整登录事务成功后再消费。
        validateSmsCode(phone, request.code());

        // 小程序验证码登录复用手机号公共流程：查建账号、设备审计、签发令牌和记录日志。
        LoginResponse response = doLoginByPhone(phone, request.deviceId(), null, null, CLIENT_MINI_PROGRAM, "login", request.password(), request.registerSource());
        // 只有公共流程全部成功才删除验证码，避免中途数据库异常导致用户无法重试。
        redisTemplate.delete(key(SMS_CODE_KEY, DEFAULT_SCENE, phone));
        return response;
    }

    /** 手机号密码登录。 */
    @Override
    @Transactional
    public LoginResponse passwordLogin(PasswordLoginRequest request) {
        String phone = request.phone();
        // 密码错误与验证码错误共用手机号失败窗口，防止攻击者交替使用入口绕过限制。
        validateLoginFailLimit(phone);

        // 密码入口只允许已有账号登录，不能像验证码入口一样自动注册。
        AuthAccount account = accountMapper.findByPhone(phone);
        if (account == null) {
            // 对外统一返回“手机号或密码错误”，不泄露手机号是否已经注册。
            increaseLoginFail(phone);
            insertLoginLog(null, phone, "password_login", request.deviceId(), currentIp(), false, "account not found");
            throw new BusinessException(ResultCode.UNAUTHORIZED, "手机号或密码错误");
        }

        // 禁用账号即使密码正确也不能建立新会话；该分支不增加密码错误次数。
        if (Integer.valueOf(2).equals(account.getAccountStatus())) {
            insertLoginLog(account.getUserId(), phone, "password_login", request.deviceId(), currentIp(), false, "account disabled");
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }

        // 密码凭证与账号分表存储，验证码/微信注册的用户可能尚未创建密码凭证。
        AuthPasswordCredential credential = passwordCredentialMapper.findByUserId(account.getUserId());
        if (credential == null || !Integer.valueOf(1).equals(credential.getPasswordStatus())) {
            insertLoginLog(account.getUserId(), phone, "password_login", request.deviceId(), currentIp(), false, "password credential missing");
            throw new BusinessException(ResultCode.BAD_REQUEST, "账号未设置密码，请先使用验证码登录设置密码");
        }

        // BCrypt.matches 会读取哈希中自带的盐和成本参数；禁止重新 encode 后直接比较字符串。
        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            increaseLoginFail(phone);
            insertLoginLog(account.getUserId(), phone, "password_login", request.deviceId(), currentIp(), false, "password invalid");
            throw new BusinessException(ResultCode.UNAUTHORIZED, "手机号或密码错误");
        }

        // 客户端类型只影响设备审计；所有类型最终仍归入同一个账号级会话域。
        String clientType = passwordClientType(request.clientType());
        String ip = currentIp();
        // 先更新账号与设备的最近登录快照，便于后台审计本次成功登录来源。
        updateLastLogin(account.getUserId(), ip);
        upsertDeviceBinding(account, clientType, request.deviceId(), null, null, ip);
        // 成功登录立即清空历史失败窗口，避免旧错误次数影响下一次正常登录。
        redisTemplate.delete(loginFailKey(phone));

        // 创建新令牌对会覆盖账号当前 JTI，并使其他终端的旧会话立即变为 KICKED。
        TokenStore.TokenPair tokenPair = tokenStore.create(
                account.getUserId(), phone, normalizeDeviceId(request.deviceId()), SESSION_SCOPE_ACCOUNT);
        // 日志在令牌成功签发后写入，只有完整成功链路才记录 success=1。
        insertLoginLog(account.getUserId(), phone, "password_login", request.deviceId(), ip, true, "password login success");
        return new LoginResponse(
                tokenPair.token(),
                tokenPair.refreshToken(),
                account.getUserId(),
                false,
                true,
                isMiniInviteOnboardingCompleted(account),
                tokenPair.expireSeconds()
        );
    }

    /** 微信手机号授权登录。 */
    @Override
    @Transactional
    public LoginResponse wxPhoneLogin(WxPhoneLoginRequest request) {
        String phone;
        if (StringUtils.hasText(request.mockPhone())) {
            // 联调模式显式传入模拟手机号时不访问微信网络接口。
            phone = request.mockPhone().trim();
        } else {
            // 正式模式必须携带 wx.getPhoneNumber 产生的一次性 code。
            if (!StringUtils.hasText(request.code())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "微信授权 code 或 mockPhone 至少传一个");
            }
            // 第三方客户端负责 access_token 获取、微信错误码转换及手机号完整性检查。
            phone = wxMiniProgramClient.getPhoneNumber(request.code());
        }

        // 优先使用结构化来源；仅当新字段为空时兼容旧版 inviteCode 请求。
        RegisterSource source = request.registerSource();
        if (source == null && StringUtils.hasText(request.inviteCode())) {
            source = new RegisterSource("INVITE", request.inviteCode().trim());
        }
        // 微信只负责取得可信手机号，后续账号和令牌规则与普通手机号登录完全一致。
        return doLoginByPhone(phone, request.deviceId(), null, null, CLIENT_MINI_PROGRAM,
                "wx_phone_login", request.password(), source);
    }

    /** 当前登录用户首次设置密码；重复设置不会覆盖旧密码。 */
    @Override
    @Transactional
    public void setPassword(String authorization, SetPasswordRequest request) {
        // 必须先由 access token 确认当前 userId，客户端不能替其他用户指定密码。
        AuthPrincipal principal = resolvePrincipal(authorization);
        // 服务层再次做业务规则校验，不能只依赖 DTO 长度注解。
        validateSetPassword(request.password());
        AuthPasswordCredential existing = passwordCredentialMapper.findByUserId(principal.userId());
        if (existing != null) {
            // 重复提交相同密码按幂等成功处理，适配客户端超时后的安全重试。
            if (passwordEncoder.matches(request.password(), existing.getPasswordHash())) {
                return;
            }
            // 已存在不同密码时拒绝覆盖；修改密码必须走独立的身份复核流程。
            throw new BusinessException(409, "账号已经设置密码");
        }
        // 只把 BCrypt 哈希与算法版本写入凭证表，明文生命周期到此方法结束。
        createPasswordCredential(principal.userId(), request.password());
    }

    /** 邀请码引导页展示后立即落库，确保用户中途退出后不再被强制跳回。 */
    @Override
    @Transactional
    public void completeMiniInviteOnboarding(String authorization) {
        // 引导完成状态只能更新当前认证主体，不能由请求体伪造 userId。
        AuthPrincipal principal = resolvePrincipal(authorization);
        // SQL 是幂等置 1；重复进入页面不会产生状态回退。
        accountMapper.completeMiniInviteOnboarding(principal.userId(), LocalDateTime.now());
    }

    /** App 驾驶端手机号验证码登录。 */
    @Override
    @Transactional
    public LoginResponse appLogin(AppLoginRequest request) {
        String phone = request.phone();
        // App 与小程序共享相同的手机号试错计数和验证码存储。
        validateLoginFailLimit(phone);
        validateSmsCode(phone, request.code());

        // App 额外传入设备名称和平台，用于建立更完整的终端审计记录。
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
        // 完整登录成功后消费验证码，防止同一验证码重复建立多个会话。
        redisTemplate.delete(key(SMS_CODE_KEY, DEFAULT_SCENE, phone));
        return response;
    }

    /**
     * 手机号登录公共流程。
     *
     * <p>手机号验证码登录和微信手机号登录最终都会走到这里：查账号、必要时创建账号、签发 Token、记录日志。</p>
     */
    private LoginResponse doLoginByPhone(String phone, String deviceId, String deviceName, String platform,
                                         String clientType, String actionType, String password, RegisterSource registerSource) {
        // 手机号是认证账号的唯一登录定位键；查询不到代表本次需要创建新账号。
        AuthAccount account = accountMapper.findByPhone(phone);
        boolean isNewUser = account == null;
        if (account == null) {
            // 账号与业务 userId 在当前事务中一起生成，后续模块统一引用 userId。
            account = createAccount(phone, currentIp());
            if (StringUtils.hasText(password)) {
                // 只有首次创建账号时才接受初始密码，已有账号绝不会被登录请求覆盖密码。
                validateInitialPassword(password);
                createPasswordCredential(account.getUserId(), password);
            }
            // 注册事件只发布一次，确保邀请/推广关系保持首次归因。
            publishUserRegistered(account, registerSource);
        }

        // 无论新旧账号，禁用状态都是建立会话前的最终业务闸门。
        if (Integer.valueOf(2).equals(account.getAccountStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账号已被禁用");
        }
        // 响应中的 passwordSet 来自真实凭证表，用于前端决定是否展示初始化提示。
        boolean passwordSet = hasPasswordCredential(account.getUserId());

        String ip = currentIp();
        // 账号级与设备级最近登录信息分别更新，便于按用户或按终端审计。
        updateLastLogin(account.getUserId(), ip);
        upsertDeviceBinding(account, clientType, deviceId, deviceName, platform, ip);
        // 登录成功后清除验证码错误计数，避免用户后续被旧失败次数影响。
        redisTemplate.delete(loginFailKey(phone));

        // TokenStore 同时签发 access/refresh，并维护 Redis 当前会话与可撤销标记。
        TokenStore.TokenPair tokenPair = tokenStore.create(
                account.getUserId(), phone, normalizeDeviceId(deviceId), sessionScope(clientType));
        // 日志最后写入，表示账号、设备和令牌步骤均已成功。
        insertLoginLog(account.getUserId(), phone, actionType, deviceId, ip, true, actionType + " success");
        return new LoginResponse(
                tokenPair.token(),
                tokenPair.refreshToken(),
                account.getUserId(),
                isNewUser,
                passwordSet,
                isMiniInviteOnboardingCompleted(account),
                tokenPair.expireSeconds()
        );
    }


    /** 密码登录允许 App、商家 Web 与小程序；统一执行账号级单点登录。 */
    private String passwordClientType(String value) {
        // 老版本客户端未传类型时按驾驶端 App 处理，保持接口向后兼容。
        if (!StringUtils.hasText(value)) return CLIENT_APP_DRIVER;
        String normalized = value.trim().toUpperCase();
        // 使用显式白名单，防止任意字符串污染设备审计维度。
        if (!Set.of(CLIENT_APP_DRIVER, CLIENT_MERCHANT_WEB, CLIENT_MINI_PROGRAM).contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "客户端类型不正确");
        }
        return normalized;
    }

    private String sessionScope(String clientType) {
        // clientType 当前只用于审计；统一返回 ACCOUNT 才能保证跨端也只能保留最后一次登录。
        return SESSION_SCOPE_ACCOUNT;
    }

    /** 首次注册时校验初始密码。 */
    private void validateInitialPassword(String password) {
        // 首次创建账号必须同步建立密码，避免产生无法走密码入口的半初始化账号。
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "首次注册需要设置密码");
        }
        // 不自动 trim 密码，否则用户输入与真实凭据不同却可能登录成功。
        if (!password.equals(password.trim())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码不能包含首尾空格");
        }
        // 在允许移动端易输入的同时限制异常超长输入带来的哈希计算成本。
        if (password.length() < 8 || password.length() > 32) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码长度需为8-32位");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        // 初始注册密码要求至少两类字符，避免纯数字或纯字母弱密码。
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码需至少包含字母和数字");
        }
    }

    /** 联调初始化密码规则；允许需求示例中的纯数字六位密码。 */
    private void validateSetPassword(String password) {
        // 该入口为兼容联调数据采用较宽松规则，但仍拒绝空值和不可见的首尾空格。
        if (!StringUtils.hasText(password) || !password.equals(password.trim())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码不能为空且不能包含首尾空格");
        }
        // 上限与登录 DTO 保持一致，避免存入客户端以后无法再次提交的密码。
        if (password.length() < 6 || password.length() > 32) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码长度需为6-32位");
        }
    }

    /** 创建密码凭证，只保存不可逆哈希。 */
    private void createPasswordCredential(Long userId, String password) {
        LocalDateTime now = LocalDateTime.now();
        AuthPasswordCredential credential = new AuthPasswordCredential();
        // 凭证使用独立主键；userId 负责和认证账号建立一对一业务关系。
        credential.setId(SnowflakeIdGenerator.nextId());
        credential.setUserId(userId);
        // BCrypt.encode 内部生成随机盐，同一明文每次产生不同哈希是正常现象。
        credential.setPasswordHash(passwordEncoder.encode(password));
        // 保存算法版本，为未来登录成功后渐进式升级哈希算法预留判断依据。
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
        // 只判断凭证是否存在；Mapper 已过滤逻辑删除记录。
        return passwordCredentialMapper.findByUserId(userId) != null;
    }

    /** 新增或刷新设备绑定关系。 */
    private void upsertDeviceBinding(AuthAccount account, String clientType, String deviceId, String deviceName, String platform, String ip) {
        // 缺失设备 ID 的旧客户端统一落入 default，保证查询与 Token 声明使用相同值。
        String normalizedDeviceId = StringUtils.hasText(deviceId) ? deviceId : "default";
        LocalDateTime now = LocalDateTime.now();
        // 优先尝试更新可减少一次查询；影响 0 行时再按首次设备登录执行插入。
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
            // 已有绑定刷新成功，无需生成新主键或修改首次创建时间。
            return;
        }

        // 首次看到“用户 + 客户端类型 + 设备”组合时建立完整审计快照。
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
        // 未携带来源时仍发布注册事件，让只关心“新用户”的监听器也能工作。
        String sourceType = registerSource == null ? null : registerSource.sourceType();
        String sourceCode = registerSource == null ? null : registerSource.sourceCode();
        // 事件携带账号创建时间，邀请和推广模块可以按真实注册时刻归因。
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
        // 解析得到的 principal 同时提供 userId、设备和原始 Token，避免相信请求体身份。
        AuthPrincipal principal = resolvePrincipal(authorization);
        // compare-and-delete 只撤销当前 JTI，不会误删并发产生的新会话索引。
        tokenStore.deleteToken(principal);
        // Token 撤销完成后记录退出行为；日志中不保存原始 Token。
        insertLoginLog(principal.userId(), principal.phone(), "logout", principal.deviceId(), currentIp(), true, "logout success");
        return new LogoutResponse(true);
    }

    /** 获取当前登录用户基础信息。 */
    @Override
    public CurrentUserResponse getCurrentUser(String authorization) {
        // access token 先通过 JWT 与 Redis 双重校验，再使用其中的 userId 查询最新账号状态。
        AuthPrincipal principal = resolvePrincipal(authorization);
        AuthAccount account = accountMapper.findByUserId(principal.userId());
        // Token 对应账号不存在时不返回缓存身份，统一要求客户端重新登录。
        if (account == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已失效");
        }
        // 响应只暴露脱敏手机号和前端引导所需状态，不返回实体内部字段。
        return new CurrentUserResponse(
                account.getUserId(),
                maskPhone(account.getPhone()),
                "LOGIN",
                hasPasswordCredential(account.getUserId()),
                isMiniInviteOnboardingCompleted(account)
        );
    }

    /** 使用 refresh token 换取新的 access token。 */
    @Override
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        // resolveRefresh 同时验证 JWT、refresh 映射和账号 current JTI，并保留被顶下线语义。
        TokenStore.RefreshResolution resolution = tokenStore.resolveRefresh(request.refreshToken());
        if (resolution.status() == TokenStore.RefreshStatus.KICKED) {
            // 使用专用业务码，让客户端展示“其他设备登录”而不是普通过期提示。
            throw new BusinessException(
                    AuthSecurityExceptionHandler.ACCOUNT_LOGGED_IN_ELSEWHERE_CODE,
                    AuthSecurityExceptionHandler.ACCOUNT_LOGGED_IN_ELSEWHERE_MESSAGE);
        }
        if (resolution.status() != TokenStore.RefreshStatus.VALID) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌已失效，请重新登录");
        }
        TokenStore.RefreshPrincipal refreshPrincipal = resolution.principal();
        // 刷新前重新读取账号，避免给已删除的身份延长会话。
        AuthAccount account = accountMapper.findByUserId(refreshPrincipal.userId());
        if (account == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已失效");
        }

        // refresh token 一次性使用：刷新成功后立即删除旧 refresh token。
        tokenStore.deleteRefreshToken(request.refreshToken());
        // 新建令牌对会覆盖 current JTI，同时撤销旧 access token 的 Redis 标记。
        TokenStore.TokenPair tokenPair = tokenStore.create(
                refreshPrincipal.userId(), account.getPhone(), refreshPrincipal.deviceId(), refreshPrincipal.sessionScope());
        // 审计刷新行为但不记录 refresh token 或 JTI 等敏感值。
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
        // Redis 中不存在通常表示从未发送、已过期或已被成功登录消费。
        String savedCode = redisTemplate.opsForValue().get(redisKey);
        if (!StringUtils.hasText(savedCode)) {
            // 过期与输错都计入同一手机号失败窗口，避免无限探测验证码。
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
        // 表主键与跨模块 userId 分开生成，其他业务模块只依赖稳定的 userId。
        account.setId(SnowflakeIdGenerator.nextId());
        account.setUserId(SnowflakeIdGenerator.nextId());
        account.setPhone(phone);
        account.setMiniInviteOnboardingCompleted(0);
        account.setLastLoginIp(ip);
        account.setCreatedAt(now);
        account.setUpdatedAt(now);
        // 插入后重新按手机号查询，拿到数据库默认填充的账号状态和完整字段。
        accountMapper.insert(account);
        return accountMapper.findByPhone(phone);
    }

    private boolean isMiniInviteOnboardingCompleted(AuthAccount account) {
        // 使用显式 1 判断；null 与其他值都按未完成处理，避免空指针和误判。
        return account != null && Integer.valueOf(1).equals(account.getMiniInviteOnboardingCompleted());
    }

    /** 更新账号最近登录信息。 */
    private void updateLastLogin(Long userId, String ip) {
        LocalDateTime now = LocalDateTime.now();
        // lastLoginTime 与 updatedAt 使用同一时刻，避免一次更新出现毫秒级不一致。
        accountMapper.updateLastLogin(userId, now, ip, now);
    }

    /** 写入短信发送日志。 */
    private void insertSmsLog(String phone, String scene, boolean success, String provider, String errorMessage) {
        // 短信日志状态约定：1 成功、2 失败；不保存验证码正文。
        smsLogMapper.insert(SnowflakeIdGenerator.nextId(), phone, scene, success ? 1 : 2, provider, errorMessage);
    }

    /** 写入登录行为日志。 */
    private void insertLoginLog(Long userId, String phone, String actionType, String deviceId, String ip, boolean success, String message) {
        // 登录审计状态约定：1 成功、0 失败；账号未识别前 userId 允许为空。
        loginLogMapper.insert(SnowflakeIdGenerator.nextId(), userId, phone, actionType, deviceId, ip, success ? 1 : 0, message);
    }

    /** 登录前检查验证码错误次数是否达到上限。 */
    private void validateLoginFailLimit(String phone) {
        // 失败键依赖 Redis TTL 自动解锁；这里只读取次数，不延长原有锁定窗口。
        String failCount = redisTemplate.opsForValue().get(loginFailKey(phone));
        if (StringUtils.hasText(failCount) && Integer.parseInt(failCount) >= LOGIN_FAIL_LIMIT) {
            throw new BusinessException("登录失败次数过多，请稍后再试");
        }
    }

    /** 增加验证码错误次数，首次失败时设置 15 分钟过期。 */
    private void increaseLoginFail(String phone) {
        // INCR 对不存在的键从 1 开始，适合并发累计同一手机号的失败请求。
        Long count = redisTemplate.opsForValue().increment(loginFailKey(phone));
        if (count != null && count == 1) {
            // 仅第一次失败设置 TTL，后续错误不会不断延长十五分钟窗口。
            redisTemplate.expire(loginFailKey(phone), Duration.ofMinutes(15));
        }
    }

    /** 从 Authorization 请求头解析当前登录主体。 */
    private AuthPrincipal resolvePrincipal(String authorization) {
        // Service 层仍做请求头校验，保证直接调用业务服务时不会绕过 SecurityFilterChain。
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
        }
        String token = authorization.substring("Bearer ".length());
        // TokenStore 同时检查 JWT 签名、自然过期、access 标记和账号当前 JTI。
        return tokenStore.resolve(token)
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "登录状态已过期"));
    }

    /** 规范化短信验证码场景，空值使用默认登录场景。 */
    private String normalizeScene(String scene) {
        // 当前只有登录场景；空值映射为 login 以兼容未传 scene 的旧客户端。
        return StringUtils.hasText(scene) ? scene : DEFAULT_SCENE;
    }

    /** 规范化普通文本。 */
    private String normalize(String value) {
        // 可选设备描述统一去首尾空白，空值落库为空串而不是 null。
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    /** 规范化设备 ID，保证 Token 和设备绑定表使用同一维度。 */
    private String normalizeDeviceId(String deviceId) {
        // default 是缺失设备信息时的稳定占位值，避免 JWT、Redis 与设备表产生不同维度。
        return StringUtils.hasText(deviceId) ? deviceId.trim() : "default";
    }

    /** 按统一格式生成短信相关 Redis key。 */
    private String key(String pattern, String scene, String phone) {
        // 所有短信键统一采用“场景 + 手机号”，防止不同用途的验证码相互覆盖。
        return pattern.formatted(scene, phone);
    }

    /** 生成登录失败次数 Redis key。 */
    private String loginFailKey(String phone) {
        // 登录失败按账号手机号聚合，不区分验证码入口或密码入口。
        return LOGIN_FAIL_KEY.formatted(phone);
    }

    /** 生成短信验证码；当前为 mock 固定验证码，方便联调。 */
    private String generateCode() {
        // 仅联调环境固定；接入真实短信后应替换为安全随机验证码并保留现有 TTL/日志流程。
        return "829416";
    }

    /** 手机号脱敏展示。 */
    private String maskPhone(String phone) {
        // 非标准短值原样返回，标准手机号仅保留前三位和后四位。
        if (!StringUtils.hasText(phone) || phone.length() < 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 获取当前请求 IP，优先读取网关/代理透传的 X-Forwarded-For。 */
    private String currentIp() {
        // 后台任务或脱离 HTTP 请求调用时没有 RequestAttributes，审计 IP 使用空串。
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "";
        }
        HttpServletRequest request = attributes.getRequest();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            // 多级代理会按逗号追加地址，第一个值通常代表原始客户端。
            return forwardedFor.split(",")[0].trim();
        }
        // 未经过可信代理或未配置透传头时退回 Servlet 远端地址。
        return request.getRemoteAddr();
    }
}
