package com.tongluxing.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.tongluxing.auth.security.AuthSecurityExceptionHandler;
import com.tongluxing.auth.security.TokenAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * Spring Security 配置。
 *
 * <p>项目采用前后端分离的无状态认证：接口不创建 Session，登录态由 Bearer Token + Redis 维护。</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 自定义 Token 过滤器，负责把合法 Bearer Token 转换成 Spring Security Authentication。 */
    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    /** Security 认证/授权失败统一 JSON 输出处理器。 */
    private final AuthSecurityExceptionHandler authSecurityExceptionHandler;

    /**
     * 配置接口鉴权规则和安全过滤链。
     *
     * <p>登录、验证码、令牌刷新、健康检查和用户公开资料接口允许匿名访问，其余接口需要登录。</p>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // 前后端分离接口不依赖 Cookie，因此关闭 CSRF。
                .csrf(AbstractHttpConfigurer::disable)
                // 关闭 Spring Security 默认表单登录页。
                .formLogin(AbstractHttpConfigurer::disable)
                // 关闭 HTTP Basic，统一使用 Bearer Token。
                .httpBasic(AbstractHttpConfigurer::disable)
                // 服务端不保存 Session，登录态只看 Token 和 Redis。
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 认证链路入口放行，避免未登录用户无法登录。
                        .requestMatchers(
                                "/v1/auth/sms-code",
                                "/v1/auth/login",
                                "/v1/auth/wx-phone-login",
                                "/v1/auth/refresh-token",
                                "/actuator/health"
                        ).permitAll()
                        // 用户模块中明确属于公开展示的读取接口放行。
                        .requestMatchers(HttpMethod.GET,
                                "/v1/users/*/public-profile",
                                "/v1/users/*/homepage",
                                "/v1/customer-service/entry"
                        ).permitAll()
                        // 除上述白名单外，所有接口都必须携带有效 Token。
                        .anyRequest().authenticated()
                )
                // Security 异常不会进入 @RestControllerAdvice，这里统一输出 Result JSON。
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authSecurityExceptionHandler)
                        .accessDeniedHandler(authSecurityExceptionHandler)
                )
                // 在用户名密码过滤器前解析 Bearer Token。
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * 禁用传统用户名密码登录。
     *
     * <p>保留该 Bean 是为了满足 Spring Security 自动配置依赖，但任何调用都会直接失败。</p>
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Password login is disabled");
        };
    }
}
