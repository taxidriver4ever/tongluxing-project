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

    /** 当前密文格式标识，用于与历史 Base64 数据区分。 */
    private static final String VERSION_PREFIX = "v2:";
    /** GCM 认证标签长度；128 bit 可同时校验密文完整性和密钥正确性。 */
    private static final int GCM_TAG_BITS = 128;
    /** GCM 推荐的 96 bit IV，换算为 12 字节。 */
    private static final int IV_BYTES = 12;

    /** 由环境密钥派生出的 AES 数据加密密钥。 */
    private final SecretKeySpec encryptionKey;
    /** 独立派生的 HMAC 密钥，只用于生成稳定 IV，不能与 AES 密钥混用。 */
    private final SecretKeySpec ivKey;

    /**
     * 初始化车辆敏感数据加密组件。
     *
     * @param secret 部署环境提供的主密钥；长度不足 32 字符时拒绝启动，避免弱密钥上线
     */
    public VehicleDataCipher(@Value("${vehicle.security.data-encryption-key}") String secret) {
        if (!StringUtils.hasText(secret) || secret.trim().length() < 32) {
            throw new IllegalStateException("VEHICLE_DATA_ENCRYPTION_KEY 至少需要 32 个字符");
        }
        // 先把任意长度的配置值收敛为固定 256 bit 主密钥材料。
        byte[] master = sha256(secret.trim().getBytes(StandardCharsets.UTF_8));

        // 使用不同用途标签派生两把逻辑密钥，避免同一密钥跨 AES/HMAC 算法复用。
        this.encryptionKey = new SecretKeySpec(
                sha256(concat(master, "vehicle:aes".getBytes(StandardCharsets.UTF_8))), "AES");
        this.ivKey = new SecretKeySpec(
                sha256(concat(master, "vehicle:iv".getBytes(StandardCharsets.UTF_8))), "HmacSHA256");
    }

    /** 加密并添加版本前缀；同一规范化明文保持稳定，供车牌等值查找。 */
    public String encrypt(String value) {
        if (!StringUtils.hasText(value)) {
            // 可选敏感字段为空时保持 null，避免生成没有业务意义的密文。
            return null;
        }
        byte[] plain = value.trim().getBytes(StandardCharsets.UTF_8);
        try {
            // 合成 IV 由 HMAC(明文) 截取而来，因此同一规范化明文会生成稳定密文，支持等值查询。
            byte[] iv = Arrays.copyOf(hmac(plain), IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain);

            // 存储格式为“v2: + Base64URL(IV || GCM密文及标签)”，解密时可自包含恢复 IV。
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
            // 没有 v2 前缀说明是上线前的 Base64 数据，仅做读取兼容，不再以旧格式写入。
            return decodeLegacy(value);
        }
        try {
            // 去掉版本前缀并拆分前 12 字节 IV 与后续 GCM 密文。
            byte[] payload = Base64.getUrlDecoder().decode(value.substring(VERSION_PREFIX.length()));
            if (payload.length <= IV_BYTES) {
                throw new GeneralSecurityException("payload too short");
            }
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(payload, IV_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            // doFinal 同时完成解密和认证标签校验；数据被篡改时会直接抛出异常。
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
        // 该值只用于 Mapper 的兼容查询条件，绝不能作为新数据的“加密”结果保存。
        return Base64.getEncoder().encodeToString(value.trim().getBytes(StandardCharsets.UTF_8));
    }

    /** 解码历史 Base64 字段；格式损坏时按服务端数据异常处理。 */
    private String decodeLegacy(String value) {
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ResultCode.INTERNAL_SERVER_ERROR, "车辆历史敏感数据格式错误");
        }
    }

    /** 使用独立 IV 密钥计算 HMAC-SHA256，调用方再截取所需 IV 长度。 */
    private byte[] hmac(byte[] value) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(ivKey);
        return mac.doFinal(value);
    }

    /** 计算 SHA-256，用于把主密钥和用途标签派生为固定长度密钥材料。 */
    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    /** 连接两个字节数组；用于密钥派生材料和 IV/密文载荷拼接。 */
    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = Arrays.copyOf(left, left.length + right.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }
}
