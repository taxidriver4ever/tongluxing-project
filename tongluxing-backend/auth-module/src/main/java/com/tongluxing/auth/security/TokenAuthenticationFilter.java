package com.tongluxing.auth.security;

import java.io.IOException;
import java.util.List;
import java.util.ArrayList;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import com.tongluxing.auth.mapper.AuthRoleMapper;

/**
 * Bearer Token 认证过滤器。
 *
 * <p>每个请求只执行一次：从 Authorization 头提取 access token，校验通过后写入 Spring Security 上下文。</p>
 *
 * <p>同一个 Bearer 头可能承载两套互相隔离的会话：先尝试解析 Admin 不透明 Token，
 * 未命中后再解析普通用户 JWT。Admin 固定授予 ROLE_ADMIN；普通用户固定拥有 ROLE_USER，
 * 并在每次请求中从数据库追加最新动态角色。没有 Token 或 Token 普通失效时继续过滤链，
 * 最终由路径鉴权规则决定是否返回 401；明确被顶下线时立即返回专用业务码。</p>
 */
@Component
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    /** Token 存储与校验组件。 */
    private final TokenStore tokenStore;
    /** 与用户 JWT 隔离的 Web Admin 会话存储。 */
    private final AdminSessionStore adminSessionStore;
    /** 按用户实时查询 MERCHANT 等动态角色的 Mapper。 */
    private final AuthRoleMapper authRoleMapper;
    /** 直接写出认证失败、权限不足和被顶下线 JSON 的处理器。 */
    private final AuthSecurityExceptionHandler authSecurityExceptionHandler;

    /**
     * 解析请求中的 Token，并在有效时设置当前请求的认证信息。
     *
     * <p>本方法不把无效 Token 当场转换为异常，因为公开接口允许携带空或过期 Token；
     * SecurityFilterChain 会在访问受保护路径时调用 AuthenticationEntryPoint。</p>
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param filterChain 后续 Servlet/Security 过滤链
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 两套认证都复用 Authorization: Bearer 头，先统一提取原始 Token。
        String token = resolveToken(request);

        // Admin 使用不透明随机 Token，优先尝试独立 Admin Redis 会话。
        AdminSessionStore.AdminResolution adminResolution = adminSessionStore.resolveDetailed(token);
        if (adminResolution.status() == AdminSessionStore.AdminStatus.KICKED) {
            // 明确被其他后台登录顶下线时直接结束过滤链，返回前端可识别的专用业务码。
            authSecurityExceptionHandler.accountLoggedInElsewhere(response);
            return;
        }
        if (adminResolution.status() == AdminSessionStore.AdminStatus.VALID) {
            var session = adminResolution.session();
            // 复用轻量 AuthPrincipal：Admin 的 username 放在 phone 字段，设备固定标记为 admin-web。
            AuthPrincipal principal = new AuthPrincipal(
                    session.operatorId(), session.username(), token, "admin-web");
            // Admin 权限来自独立会话，固定授予 ROLE_ADMIN，不查询普通用户角色表。
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
            // 写入当前线程 SecurityContext，后续 URL 授权与业务代码都从这里读取主体。
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
            // Admin 未命中后再按普通用户 JWT 解析，避免把不透明 Token 交给 JWT 业务路径。
            TokenStore.AccessResolution resolution = tokenStore.resolveAccessToken(token);
            if (resolution.status() == TokenStore.AccessStatus.KICKED) {
                // JWT 合法但 current JTI 已变化，说明账号后来在其他终端登录。
                authSecurityExceptionHandler.accountLoggedInElsewhere(response);
                return;
            }
            if (resolution.status() == TokenStore.AccessStatus.VALID) {
                AuthPrincipal principal = resolution.principal();
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                // 每个普通认证账号都具备基础用户角色。
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                // 商家等业务角色每次请求实时查询，使审核通过/撤销无需重新签发 JWT。
                authRoleMapper.findRoleCodes(principal.userId()).stream()
                        // Spring Security hasRole("X") 实际匹配 ROLE_X，因此统一补前缀。
                        .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                        .forEach(authorities::add);
                // credentials 传 null，避免在 SecurityContext 中保存任何密码或验证码。
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        // 空 Token 或普通 INVALID 不在这里立即报错：公开接口仍可继续，受保护接口由授权规则返回 401。
        filterChain.doFilter(request, response);
    }

    /**
     * 从 {@code Authorization: Bearer xxx} 请求头提取 Token。
     *
     * @return Token 文本；头缺失或前缀不正确时返回空串，让解析组件按 INVALID 处理
     */
    private String resolveToken(HttpServletRequest request) {
        // 只读取标准 Authorization 头，不接受查询参数，避免 Token 出现在 URL 和访问日志。
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            // 返回空串而非抛异常，让公开接口可以在未登录状态正常访问。
            return "";
        }
        // 保留 Token 本体，不在过滤器中解析格式；具体校验交给对应会话存储。
        return authorization.substring("Bearer ".length());
    }
}
