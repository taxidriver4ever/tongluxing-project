package com.tongluxing.auth.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.tongluxing.auth.config.AdminAuthProperties;
import com.tongluxing.auth.dto.AdminLoginRequest;
import com.tongluxing.auth.security.AdminLoginRateLimiter;
import com.tongluxing.auth.security.AdminSessionStore;
import com.tongluxing.auth.service.AdminAuthService;
import com.tongluxing.auth.vo.AdminCurrentOperatorResponse;
import com.tongluxing.auth.vo.AdminLoginResponse;
import com.tongluxing.auth.vo.AdminLogoutResponse;
import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;

import lombok.RequiredArgsConstructor;

/**
 * 固定联调凭据的 Web Admin 登录实现。
 *
 * <p>登录顺序是：规范化用户名、检查 Redis 失败窗口、常量时间比较用户名和密码、
 * 记录或清空失败次数、创建随机不透明会话。用户名和密码采用统一错误提示，
 * 避免向调用方泄露某个用户名是否存在。</p>
 *
 * <p>该实现适用于当前单一运营账号联调。它不会读取普通用户账号表，也不会签发 JWT；
 * 后台会话的单点登录和过期控制由 {@link AdminSessionStore} 负责。</p>
 */
@Service
@RequiredArgsConstructor
public class AdminAuthServiceImpl implements AdminAuthService {
    /** 后台固定凭据、操作员信息和安全窗口配置。 */
    private final AdminAuthProperties properties;
    /** 基于 Redis 固定窗口的登录失败计数器。 */
    private final AdminLoginRateLimiter rateLimiter;
    /** 后台不透明 Token 的创建、解析和撤销组件。 */
    private final AdminSessionStore sessionStore;

    /**
     * 登录后台并创建会话。
     *
     * <p>即使用户名不匹配也会继续比较密码，使两类错误路径尽量一致。
     * 成功登录会清空失败计数，并覆盖同一用户名的当前会话索引，从而顶下旧会话。</p>
     *
     * @param request 经过 Bean Validation 的后台登录请求
     * @return 新会话 Token、操作员信息和有效期
     * @throws BusinessException 达到失败上限时返回 429，凭据错误时返回 401
     */
    @Override
    public AdminLoginResponse login(AdminLoginRequest request) {
        // 用户名去除首尾空白并转小写，使限流键和凭据比较都不受输入大小写影响。
        String username = request.username().trim().toLowerCase(Locale.ROOT);

        // 在比较密码前检查失败窗口；锁定期间不再执行任何凭据判断。
        AdminLoginRateLimiter.LimitState before = rateLimiter.state(username);
        if (before.locked()) {
            throw locked(before);
        }

        // 用户名和密码都采用常量时间字节比较，避免普通字符串短路比较泄露匹配前缀。
        boolean usernameMatches = constantTimeEquals(username,
                properties.getUsername().trim().toLowerCase(Locale.ROOT));
        boolean passwordMatches = constantTimeEquals(request.password(), properties.getPassword());
        if (!usernameMatches || !passwordMatches) {
            // 两类凭据错误共用同一计数与提示，客户端无法据此判断用户名是否存在。
            AdminLoginRateLimiter.LimitState after = rateLimiter.recordFailure(username);
            if (after.locked()) {
                // 本次失败刚好达到上限时立即返回锁定信息，而不是普通密码错误。
                throw locked(after);
            }
            // 剩余次数来自配置上限减去 Redis 当前计数，便于前端给出明确提示。
            long remaining = properties.getMaxFailures() - after.failures();
            throw new BusinessException(ResultCode.UNAUTHORIZED,
                    "用户名或密码错误，还可尝试 " + remaining + " 次");
        }

        // 成功登录清除失败窗口，避免历史错误次数影响下一次正常登录。
        rateLimiter.reset(username);
        // 创建随机不透明 Token，并覆盖该用户名的 current 会话索引以实现后台单点登录。
        AdminSessionStore.CreatedSession created = sessionStore.create(properties.getOperatorId(),
                properties.getUsername(), properties.getDisplayName());
        // 只在响应中返回一次明文 Token；Redis 保存的是其 SHA-256 摘要。
        return new AdminLoginResponse(created.token(), created.session().operatorId(),
                created.session().username(), created.session().displayName(), created.expireSeconds());
    }

    /**
     * 查询当前后台操作员。
     *
     * @param authorization Bearer 请求头
     * @return Redis 会话中保存的操作员快照和绝对过期时间
     * @throws BusinessException 请求头缺失、格式错误或会话失效时返回 401
     */
    @Override
    public AdminCurrentOperatorResponse current(String authorization) {
        // 每次查询都重新验证 Redis 会话与 current 索引，旧后台页面会在被顶下线后立即失效。
        AdminSessionStore.AdminSession session = requireSession(authorization);
        // expireAt 是绝对 Unix 秒，前端可据此计算剩余时间并主动跳转登录页。
        return new AdminCurrentOperatorResponse(session.operatorId(), session.username(),
                session.displayName(), session.expireAt());
    }

    /**
     * 校验并撤销当前后台会话。
     *
     * <p>先要求会话仍有效，再删除会话键；删除当前索引时使用比较后删除，
     * 因此旧请求不会误删同账号刚建立的新会话。</p>
     */
    @Override
    public AdminLogoutResponse logout(String authorization) {
        // 先提取 Token 供删除使用，再要求会话有效，避免无效 Token 伪装成退出成功。
        String token = resolveBearer(authorization);
        requireSession(authorization);
        // SessionStore 使用 compare-and-delete，迟到的旧退出请求不会删除新登录的 current 索引。
        sessionStore.delete(token);
        return new AdminLogoutResponse(true);
    }

    /** 从请求头解析并校验会话；对外统一隐藏失效的具体内部原因。 */
    private AdminSessionStore.AdminSession requireSession(String authorization) {
        // resolve 对 KICKED、过期、损坏和不存在统一返回空，业务接口统一提示重新登录。
        return sessionStore.resolve(resolveBearer(authorization))
                .orElseThrow(() -> new BusinessException(ResultCode.UNAUTHORIZED, "Admin 登录状态已失效"));
    }

    /**
     * 提取严格的 Bearer Token。
     *
     * <p>这里只接受区分大小写的 {@code Bearer } 前缀，返回值会去除 Token 两端空白。</p>
     */
    private String resolveBearer(String authorization) {
        // 不接受缺失值、其他认证方案或没有空格的 Bearer 前缀。
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录 Admin 后台");
        }
        // 去除 Token 两端空白，避免复制粘贴导致 Redis 摘要不一致。
        return authorization.substring("Bearer ".length()).trim();
    }

    /** 根据 Redis 剩余 TTL 构造带可读重试分钟数的 429 业务异常。 */
    private BusinessException locked(AdminLoginRateLimiter.LimitState state) {
        // retryAfterMinutes 已向上取整，剩余不足一分钟时仍提示至少一分钟。
        return new BusinessException(429,
                "密码错误已达 5 次，账号已临时锁定，请约 " + state.retryAfterMinutes() + " 分钟后再试");
    }

    /**
     * 使用 JDK 常量时间字节比较，降低字符串逐字符短路比较造成的时序侧信道。
     *
     * @param left 用户提交值
     * @param right 服务端配置值
     * @return UTF-8 字节序列是否完全相同
     */
    private boolean constantTimeEquals(String left, String right) {
        // String.valueOf 让意外 null 配置也变成可比较文本，而不是在认证路径抛出空指针。
        byte[] leftBytes = String.valueOf(left).getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = String.valueOf(right).getBytes(StandardCharsets.UTF_8);
        // MessageDigest.isEqual 按字节执行常量时间比较，适合认证凭据等敏感值。
        return MessageDigest.isEqual(leftBytes, rightBytes);
    }
}
