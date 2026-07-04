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
        if (!StringUtils.hasText(secret) || secret.length() < 32) {
            throw new IllegalStateException("auth.jwt.secret must be at least 32 characters");
        }
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.issuer = issuer;
    }

    /** 创建 access token。 */
    public JwtToken createAccessToken(Long userId, String phone, String deviceId, int ttlSeconds) {
        return createToken(userId, phone, deviceId, ACCESS_TYPE, ttlSeconds);
    }

    /** 创建 refresh token。 */
    public JwtToken createRefreshToken(Long userId, String phone, int ttlSeconds) {
        return createToken(userId, phone, "", REFRESH_TYPE, ttlSeconds);
    }

    /** 解析并校验 access token 类型。 */
    public JwtClaims parseAccessToken(String token) {
        JwtClaims claims = parse(token);
        if (!ACCESS_TYPE.equals(claims.type())) {
            throw new IllegalArgumentException("invalid token type");
        }
        return claims;
    }

    /** 解析并校验 refresh token 类型。 */
    public JwtClaims parseRefreshToken(String token) {
        JwtClaims claims = parse(token);
        if (!REFRESH_TYPE.equals(claims.type())) {
            throw new IllegalArgumentException("invalid token type");
        }
        return claims;
    }

    /** 根据用户信息、令牌类型和有效期创建完整 JWT。 */
    private JwtToken createToken(Long userId, String phone, String deviceId, String type, int ttlSeconds) {
        long now = Instant.now().getEpochSecond();
        // jti 是令牌唯一编号，用于 Redis 中记录和撤销单个令牌。
        String jti = Long.toUnsignedString(SnowflakeIdGenerator.nextId(), 36);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", issuer);
        claims.put("u", userId);
        claims.put("p", phone);
        claims.put("d", StringUtils.hasText(deviceId) ? deviceId : "");
        claims.put("t", type);
        claims.put("j", jti);
        claims.put("iat", now);
        claims.put("exp", now + ttlSeconds);

        String unsignedToken = base64Json(header) + "." + base64Json(claims);
        String token = unsignedToken + "." + sign(unsignedToken);
        return new JwtToken(token, jti, now + ttlSeconds);
    }

    /** 解析 JWT，并完成格式、签名、签发方和过期时间校验。 */
    private JwtClaims parse(String token) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("token is blank");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("invalid token format");
        }
        String unsignedToken = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsignedToken), parts[2])) {
            throw new IllegalArgumentException("invalid token signature");
        }

        Map<String, Object> claims = readJson(parts[1]);
        if (!issuer.equals(asString(claims.get("iss")))) {
            throw new IllegalArgumentException("invalid token issuer");
        }
        long exp = asLong(claims.get("exp"));
        if (exp <= Instant.now().getEpochSecond()) {
            throw new IllegalArgumentException("token expired");
        }
        return new JwtClaims(
                asLong(claims.get("u")),
                asString(claims.get("p")),
                asString(claims.get("d")),
                asString(claims.get("t")),
                asString(claims.get("j")),
                exp
        );
    }

    /** 将 Map 序列化为 JSON 后做 Base64URL 编码。 */
    private String base64Json(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("jwt json serialize failed", exception);
        }
    }

    /** 将 Base64URL 编码的 JWT payload 解析为 Map。 */
    private Map<String, Object> readJson(String base64) {
        try {
            byte[] json = BASE64_URL_DECODER.decode(base64);
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid token payload", exception);
        }
    }

    /** 使用 HMAC SHA-256 对 header.payload 做签名。 */
    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
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
        if (expectedBytes.length != actualBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < expectedBytes.length; i++) {
            result |= expectedBytes[i] ^ actualBytes[i];
        }
        return result == 0;
    }

    /** 安全转换为字符串，空值转为空串。 */
    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    /** 安全转换为 long，兼容 Jackson 反序列化出的 Number 或 String。 */
    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    /** JWT 生成结果，包含令牌字符串、唯一编号和过期时间戳。 */
    public record JwtToken(String token, String jti, Long expiresAt) {
    }

    /** JWT 中承载的业务声明。 */
    public record JwtClaims(Long userId, String phone, String deviceId, String type, String jti, Long expiresAt) {
    }
}
