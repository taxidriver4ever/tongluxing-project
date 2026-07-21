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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
                                "/v1/admin/auth/login",
                                "/v1/auth/sms-code",
                                "/v1/auth/login",
                                "/v1/auth/password-login",
                                "/v1/auth/wx-phone-login",
                                "/v1/auth/app/login",
                                "/v1/auth/app/bind-by-mini-ticket",
                                "/v1/auth/refresh-token",
                                "/v1/invites/qr/validate",
                                "/actuator/health"
                        ).permitAll()
                        // Web Admin 与普通用户登录态隔离，后台接口仅允许 ROLE_ADMIN。
                        .requestMatchers("/v1/admin/**", "/admin/**").hasRole("ADMIN")
                        .requestMatchers("/internal/**").hasRole("ADMIN")
                        // 订单补偿与超时关闭属于内部运维能力，普通 App 用户不得调用。
                        .requestMatchers("/v1/orders/internal/**").hasRole("ADMIN")
                        .requestMatchers("/v1/verifications/internal/**").hasRole("ADMIN")
                        // App 用户只负责生成自己的展码；解析、确认、记录与冲正属于审核通过的商家能力。
                        .requestMatchers(HttpMethod.POST, "/v1/verifications/codes").hasRole("USER")
                        .requestMatchers("/v1/verifications/**").hasRole("MERCHANT")
                        .requestMatchers("/v1/assessments/merchants/me").hasRole("MERCHANT")
                        // 用户模块中明确属于公开展示的读取接口放行。
                        .requestMatchers(HttpMethod.GET,
                                "/v1/users/*/public-profile",
                                "/v1/users/*/homepage",
                                "/v1/customer-service/entry"
                        ).permitAll()
                        // 入驻申请属于普通用户能力；其余商家经营接口必须拥有审核后授予的 MERCHANT 角色。
                        .requestMatchers(HttpMethod.POST, "/v1/merchants/applications").hasRole("USER")
                        .requestMatchers(HttpMethod.GET, "/v1/merchants/applications/me/latest").hasRole("USER")
                        .requestMatchers("/v1/merchants/**").hasRole("MERCHANT")
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
     * 禁用 Spring Security 默认表单用户名密码登录。
     *
     * <p>业务密码登录由 `/v1/auth/password-login` 接口自行校验并签发 JWT。</p>
     */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("Password login is disabled");
        };
    }

    /** 密码哈希器，当前仅用于首次注册设置密码。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
