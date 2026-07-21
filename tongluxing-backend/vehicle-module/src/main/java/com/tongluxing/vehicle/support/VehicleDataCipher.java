package com.tongluxing.vehicle.support;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.tongluxing.common.exception.BusinessException;
import com.tongluxing.common.result.ResultCode;

/**
 * 车辆敏感字段的版本化加密组件。
 *
 * <p>车牌需要支持当前用户内的等值查找，因此使用由 HMAC 生成的合成 IV，
 * 相同明文稳定得到相同密文；VIN 和发动机号复用相同格式。AES-GCM 同时提供
 * 机密性和完整性校验。旧版本仅做 Base64 编码，本组件保留只读兼容。</p>
 */
@Component
public class VehicleDataCipher {

    private static final String VERSION_PREFIX = "v2:";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final SecretKeySpec encryptionKey;
    private final SecretKeySpec ivKey;

    public VehicleDataCipher(@Value("${vehicle.security.data-encryption-key}") String secret) {
        if (!StringUtils.hasText(secret) || secret.trim().length() < 32) {
            throw new IllegalStateException("VEHICLE_DATA_ENCRYPTION_KEY 至少需要 32 个字符");
        }
        byte[] master = sha256(secret.trim().getBytes(StandardCharsets.UTF_8));
        this.encryptionKey = new SecretKeySpec(
                sha256(concat(master, "vehicle:aes".getBytes(StandardCharsets.UTF_8))), "AES");
        this.ivKey = new SecretKeySpec(
                sha256(concat(master, "vehicle:iv".getBytes(StandardCharsets.UTF_8))), "HmacSHA256");
    }

    /** 加密并添加版本前缀；同一规范化明文保持稳定，供车牌等值查找。 */
    public String encrypt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        byte[] plain = value.trim().getBytes(StandardCharsets.UTF_8);
        try {
            byte[] iv = Arrays.copyOf(hmac(plain), IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);
            return VERSION_PREFIX + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(concat(iv, encrypted));
        } catch (GeneralSecurityException exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "车辆敏感数据加密失败");
        }
    }

    /** 解密 v2 数据；没有版本前缀的数据按历史 Base64 格式只读兼容。 */
    public String decrypt(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        if (!value.startsWith(VERSION_PREFIX)) {
            return decodeLegacy(value);
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(value.substring(VERSION_PREFIX.length()));
            if (payload.length <= IV_BYTES) {
                throw new GeneralSecurityException("payload too short");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "车辆敏感数据解密失败");
        }
    }

    /** 仅用于升级期查询旧车辆档案，不用于写入新数据。 */
    public String legacyEncoded(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return Base64.getEncoder().encodeToString(value.trim().getBytes(StandardCharsets.UTF_8));
    }

    private String decodeLegacy(String value) {
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "车辆历史敏感数据格式错误");
        }
    }

    private byte[] hmac(byte[] value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(ivKey);
        return mac.doFinal(value);
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }
}
