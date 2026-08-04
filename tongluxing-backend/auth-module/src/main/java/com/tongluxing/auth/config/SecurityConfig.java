package com.tongluxing.auth.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.tongluxing.auth.security.AuthSecurityExceptionHandler;
import com.tongluxing.auth.security.TokenAuthenticationFilter;

import lombok.RequiredArgsConstructor;

/**
 * Spring Security 配置。
 *
 * <p>项目采用前后端分离的无状态认证：接口不创建 Session，登录态由 Bearer Token + Redis 维护。</p>
 *
 * <p>规则声明顺序具有语义：具体公开入口和角色路径必须放在 {@code anyRequest}
 * 之前；同一路径命中首条规则后不再继续匹配。普通用户、商家和 Admin 最终都由
 * {@link TokenAuthenticationFilter} 建立 Authentication，但角色来源不同。</p>
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 自定义 Token 过滤器，负责把合法 Bearer Token 转换成 Spring Security Authentication。 */
    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    /** Security 认证/授权失败统一 JSON 输出处理器。 */
    private final AuthSecurityExceptionHandler authSecurityExceptionHandler;
    /** 仅允许部署环境显式声明的管理端开发来源跨域访问。 */
    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    /**
     * 配置接口鉴权规则和安全过滤链。
     *
     * <p>登录、验证码、令牌刷新、健康检查和用户公开资料接口允许匿名访问，其余接口需要登录。</p>
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // 通过一条显式过滤链集中声明认证方式、路径权限和异常响应，避免使用框架默认行为。
        return http
                // 前后端分离接口不依赖 Cookie，因此关闭 CSRF。
                .csrf(AbstractHttpConfigurer::disable)
                // 同源生产部署无需 CORS；本地开发来源由环境变量白名单控制。
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
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
                                "/v1/auth/refresh-token",
                                // 腾讯 IM 服务器没有用户 Bearer Token，回调自身使用 Sign + RequestTime 鉴权。
                                "/v1/callbacks/tencent-im",
                                "/v1/invites/qr/validate",
                                "/health",
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
     * 配置不携带 Cookie 的受控跨域访问，禁止“任意来源 + 凭证”组合。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("Authorization", "Content-Type", "X-Request-Id"));
        configuration.setExposedHeaders(java.util.List.of("X-Request-Id"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * 禁用 Spring Security 默认表单用户名密码登录。
     *
     * <p>业务密码登录由 `/v1/auth/password-login` 接口自行校验并签发 JWT。</p>
     */
    @Bean
    public UserDetailsService userDetailsService() {
        // 故意提供始终失败的实现，防止 Spring 自动配置生成临时用户或启用表单密码认证。
        return username -> {
            // 业务密码登录必须走 AuthController，由其执行限流、日志、设备绑定和 Token 签发。
            throw new UsernameNotFoundException("Password login is disabled");
        };
    }

    /**
     * 提供 BCrypt 密码哈希器。
     *
     * <p>BCrypt 自动生成随机盐并把成本参数写入哈希文本；验证时必须调用
     * {@code matches}，不能对两次 encode 结果直接比较。</p>
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt 每次 encode 都生成随机盐；验证密码应调用 matches 而不是比较哈希文本。
        return new BCryptPasswordEncoder();
    }
}
