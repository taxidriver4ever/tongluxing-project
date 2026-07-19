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
 */
@Component
@RequiredArgsConstructor
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    /** Token 存储与校验组件。 */
    private final TokenStore tokenStore;
    /** 与用户 JWT 隔离的 Web Admin 会话存储。 */
    private final AdminSessionStore adminSessionStore;
    private final AuthRoleMapper authRoleMapper;

    /** 解析请求中的 Token，并在有效时设置当前请求的认证信息。 */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);
        var adminSession = adminSessionStore.resolve(token);
        if (adminSession.isPresent()) {
            var session = adminSession.get();
            AuthPrincipal principal = new AuthPrincipal(
                    session.operatorId(), session.username(), token, "admin-web");
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
            tokenStore.resolve(token).ifPresent(principal -> {
                List<SimpleGrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                authRoleMapper.findRoleCodes(principal.userId()).stream()
                        .map(code -> new SimpleGrantedAuthority("ROLE_" + code))
                        .forEach(authorities::add);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        authorities
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            });
        }
        filterChain.doFilter(request, response);
    }

    /** 从 Authorization: Bearer xxx 请求头中提取 token。 */
    private String resolveToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return "";
        }
        return authorization.substring("Bearer ".length());
    }
}
