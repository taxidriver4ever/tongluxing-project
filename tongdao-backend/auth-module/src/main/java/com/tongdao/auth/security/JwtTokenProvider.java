package com.tongdao.auth.security;

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
import com.tongdao.common.utils.SnowflakeIdGenerator;

@Component
public class JwtTokenProvider {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String ACCESS_TYPE = "a";
    private static final String REFRESH_TYPE = "r";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;
    private final String issuer;

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

    public JwtToken createAccessToken(Long userId, String phone, String deviceId, int ttlSeconds) {
        return createToken(userId, phone, deviceId, ACCESS_TYPE, ttlSeconds);
    }

    public JwtToken createRefreshToken(Long userId, String phone, int ttlSeconds) {
        return createToken(userId, phone, "", REFRESH_TYPE, ttlSeconds);
    }

    public JwtClaims parseAccessToken(String token) {
        JwtClaims claims = parse(token);
        if (!ACCESS_TYPE.equals(claims.type())) {
            throw new IllegalArgumentException("invalid token type");
        }
        return claims;
    }

    public JwtClaims parseRefreshToken(String token) {
        JwtClaims claims = parse(token);
        if (!REFRESH_TYPE.equals(claims.type())) {
            throw new IllegalArgumentException("invalid token type");
        }
        return claims;
    }

    private JwtToken createToken(Long userId, String phone, String deviceId, String type, int ttlSeconds) {
        long now = Instant.now().getEpochSecond();
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

    private String base64Json(Map<String, Object> value) {
        try {
            return BASE64_URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("jwt json serialize failed", exception);
        }
    }

    private Map<String, Object> readJson(String base64) {
        try {
            byte[] json = BASE64_URL_DECODER.decode(base64);
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid token payload", exception);
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secret, HMAC_SHA256));
            return BASE64_URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("jwt sign failed", exception);
        }
    }

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

    private String asString(Object value) {
        return value == null ? "" : value.toString();
    }

    private long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    public record JwtToken(String token, String jti, Long expiresAt) {
    }

    public record JwtClaims(Long userId, String phone, String deviceId, String type, String jti, Long expiresAt) {
    }
}
