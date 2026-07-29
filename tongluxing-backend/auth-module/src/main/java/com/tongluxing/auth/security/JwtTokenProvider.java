package com.tongluxing.auth.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongluxing.common.utils.SnowflakeIdGenerator;

/**
 * JWT 生成与解析组件。
 *
 * <p>本组件只负责 JWT 本身的签发、验签和声明解析；令牌是否仍然有效由 {@link TokenStore} 结合 Redis 判断。</p>
 *
 * <p>令牌使用 HS256，payload 采用短字段名以减小请求头体积：{@code u} 用户 ID、
 * {@code p} 手机号、{@code d} 设备、{@code s} 会话域、{@code t} 令牌类型、
 * {@code j} 唯一 JTI。解析时必须同时通过三段格式、常量时间签名比较、issuer、
 * exp 和 access/refresh 类型校验。</p>
 */
@Component
public class JwtTokenProvider {

    /** JWT 使用的 HMAC SHA-256 签名算法。 */
    private static final String HMAC_SHA256 = "HmacSHA256";
    /** access token 类型标识。 */
    private static final String ACCESS_TYPE = "a";
    /** refresh token 类型标识。 */
    private static final String REFRESH_TYPE = "r";
    /** JWT header/payload/signature 使用 Base64URL 编码且不带 padding。 */
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    /** JSON 序列化工具，用于生成和解析 JWT header/payload。 */
    private final ObjectMapper objectMapper;
    /** HMAC 签名密钥字节数组。 */
    private final byte[] secret;
    /** JWT 签发方，用于解析时校验令牌来源。 */
    private final String issuer;

    /**
     * 构造 JWT 工具。
     *
     * <p>启动时校验密钥长度，避免过短密钥导致签名安全性不足。</p>
     */
    public JwtTokenProvider(
            ObjectMapper objectMapper,
            @Value("${auth.jwt.secret}") String secret,
            @Value("${auth.jwt.issuer}") String issuer
    ) {
        // HS256 密钥过短会显著降低抗暴力破解能力，因此在应用启动阶段直接拒绝。
        if (!StringUtils.hasText(secret) || secret.length() < 32) {
            throw new IllegalStateException("auth.jwt.secret must be at least 32 characters");
        }
        // ObjectMapper 复用应用统一 JSON 配置；密钥只保存在内存字节数组中。
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        // issuer 会写入每个 Token，并在解析时严格匹配，避免接受其他系统签发的 JWT。
        this.issuer = issuer;
    }

    /**
     * 创建短期 access token。
     *
     * @return Token 文本、JTI 与绝对过期时间
     */
    public JwtToken createAccessToken(Long userId, String phone, String deviceId, String sessionScope, int ttlSeconds) {
        // access 类型使用短标识 a，解析入口会拒绝 refresh 类型。
        return createToken(userId, phone, deviceId, sessionScope, ACCESS_TYPE, ttlSeconds);
    }

    /**
     * 创建长期 refresh token。
     *
     * <p>refresh 与 access 使用相同签名密钥但携带不同类型声明，解析入口严格区分，
     * 因此 refresh token 不能直接访问业务接口。</p>
     */
    public JwtToken createRefreshToken(Long userId, String phone, String deviceId, String sessionScope, int ttlSeconds) {
        // refresh 类型使用独立标识 r，不能直接建立业务请求认证主体。
        return createToken(userId, phone, deviceId, sessionScope, REFRESH_TYPE, ttlSeconds);
    }

    /** 解析并校验 access token 类型。 */
    public JwtClaims parseAccessToken(String token) {
        // 先完成通用结构、签名、签发方与过期时间校验。
        JwtClaims claims = parse(token);
        // 再校验用途，防止 refresh token 被当作 access token 访问接口。
        if (!ACCESS_TYPE.equals(claims.type())) {
            throw new IllegalArgumentException("invalid token type");
        }
        return claims;
    }

    /** 解析并校验 refresh token 类型。 */
    public JwtClaims parseRefreshToken(String token) {
        // 通用解析成功只代表 JWT 可信，还必须验证它是刷新用途。
        JwtClaims claims = parse(token);
        if (!REFRESH_TYPE.equals(claims.type())) {
            throw new IllegalArgumentException("invalid token type");
        }
        return claims;
    }

    /** 根据用户信息、令牌类型和有效期创建完整 JWT。 */
    private JwtToken createToken(Long userId, String phone, String deviceId, String sessionScope, String type, int ttlSeconds) {
        // JWT 时间声明统一使用 Unix 秒，避免时区对令牌有效期产生影响。
        long now = Instant.now().getEpochSecond();
        // jti 是令牌唯一编号，用于 Redis 中记录和撤销单个令牌。
        String jti = Long.toUnsignedString(SnowflakeIdGenerator.nextId(), 36);
        Map<String, Object> header = new LinkedHashMap<>();
        // alg 与实际签名实现保持一致；typ 便于调试工具识别 JWT。
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> claims = new LinkedHashMap<>();
        // 使用短字段名减小每个 HTTP 请求 Authorization Header 的体积。
        claims.put("iss", issuer);
        claims.put("u", userId);
        claims.put("p", phone);
        claims.put("d", StringUtils.hasText(deviceId) ? deviceId : "");
        claims.put("s", StringUtils.hasText(sessionScope) ? sessionScope : "ACCOUNT");
        claims.put("t", type);
        claims.put("j", jti);
        claims.put("iat", now);
        claims.put("exp", now + ttlSeconds);

        // 签名输入严格为 Base64URL(header) + "." + Base64URL(payload)。
        String unsignedToken = base64Json(header) + "." + base64Json(claims);
        // 最终第三段是对前两段的 HMAC-SHA256 签名。
        String token = unsignedToken + "." + sign(unsignedToken);
        return new JwtToken(token, jti, now + ttlSeconds);
    }

    /** 解析 JWT，并完成格式、签名、签发方和过期时间校验。 */
    private JwtClaims parse(String token) {
        // 空文本不是合法 JWT，提前拒绝可避免后续 split 和解码异常。
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("token is blank");
        }
        String[] parts = token.split("\\.");
        // JWT 必须且只能有 header、payload、signature 三段。
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid token format");
        }
        String unsignedToken = parts[0] + "." + parts[1];
        // 重新计算签名并做常量时间比较，签名失败时绝不读取或信任 payload。
        if (!constantTimeEquals(sign(unsignedToken), parts[2])) {
            throw new IllegalArgumentException("invalid token signature");
        }

        // 只有签名通过后才解析声明，防止攻击者提供的未认证数据参与业务判断。
        Map<String, Object> claims = readJson(parts[1]);
        // issuer 必须与本服务配置完全一致，隔离其他系统使用同类格式的 Token。
        if (!issuer.equals(asString(claims.get("iss")))) {
            throw new IllegalArgumentException("invalid token issuer");
        }
        long exp = asLong(claims.get("exp"));
        // exp 等于当前秒时已经失效，避免边界时刻继续接受 Token。
        if (exp <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("token expired");
        }
        // 解析后的 record 是后续 Redis 校验唯一使用的可信声明载体。
        return new JwtClaims(
                asLong(claims.get("u")),
                asString(claims.get("p")),
                asString(claims.get("d")),
                StringUtils.hasText(asString(claims.get("s"))) ? asString(claims.get("s")) : "ACCOUNT",
                asString(claims.get("t")),
                asString(claims.get("j")),
                exp
        );
    }

    /** 将 Map 序列化为 JSON 后做 Base64URL 编码。 */
    private String base64Json(Map<String, Object> value) {
        try {
            // JWT 使用 Base64URL 无 padding 编码，不是普通 Base64。
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            // 服务端自身声明无法序列化属于配置/编程错误，不能签发不完整 Token。
            throw new IllegalStateException("jwt json serialize failed", exception);
        }
    }

    /** 将 Base64URL 编码的 JWT payload 解析为 Map。 */
    private Map<String, Object> readJson(String base64) {
        try {
            // payload 先做 URL 安全 Base64 解码，再解析成声明 Map。
            byte[] json = BASE64_URL_DECODER.decode(base64);
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            // 非法编码、非 JSON 或字段结构异常统一转换为无效 Token。
            throw new IllegalArgumentException("invalid token payload", exception);
        }
    }

    /** 使用 HMAC SHA-256 对 header.payload 做签名。 */
    private String sign(String value) {
        try {
            // 每次创建独立 Mac 实例；Mac 不是线程安全对象，不能作为静态共享变量。
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            // 签名结果继续使用 Base64URL 无 padding，构成 JWT 第三段。
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("jwt sign failed", exception);
        }
    }

    /**
     * 常量时间比较签名，降低根据比较耗时推测签名内容的风险。
     */
    private boolean constantTimeEquals(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        // 长度不同不可能相等，先拒绝也避免后续数组越界。
        if (expectedBytes.length != actualBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < expectedBytes.length; i++) {
            // 累积全部字节差异，不在首个不同字节处提前返回。
            result |= expectedBytes[i] ^ actualBytes[i];
        }
        return result == 0;
    }

    /** 安全转换为字符串，空值转为空串。 */
    private String asString(Object value) {
        // 缺失可选声明返回空串，后续显式业务校验决定是否接受。
        return value == null ? "" : value.toString();
    }

    /** 安全转换为 long，兼容 Jackson 反序列化出的 Number 或 String。 */
    private long asLong(Object value) {
        // Jackson 通常把 JSON 数字解析为 Number，直接取 long 可避免字符串往返。
        if (value instanceof Number number) {
            return number.longValue();
        }
        // 兼容历史 Token 中以字符串形式保存的数字声明；非法值会抛出并判定 Token 无效。
        return Long.parseLong(value.toString());
    }

    /**
     * JWT 生成结果。
     *
     * @param token 完整的 header.payload.signature 文本
     * @param jti 令牌唯一编号，用作 Redis 键关联
     * @param expiresAt 绝对过期 Unix 秒
     */
    public record JwtToken(String token, String jti, Long expiresAt) {
    }

    /**
     * 验签并校验后的 JWT 业务声明。
     *
     * @param userId 全局业务用户 ID
     * @param phone 登录手机号
     * @param deviceId 签发时的设备标识
     * @param sessionScope 会话域，当前为 ACCOUNT
     * @param type a 表示 access，r 表示 refresh
     * @param jti 令牌唯一编号
     * @param expiresAt 绝对过期 Unix 秒
     */
    public record JwtClaims(Long userId, String phone, String deviceId, String sessionScope, String type, String jti, Long expiresAt) {
    }
}
