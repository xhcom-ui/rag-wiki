package com.zhiwiki.common.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 数据加密/解密工具
 * 
 * 支持两种模式：
 * 1. AES-256-GCM - 用于存储加密（如敏感字段加密存储到数据库）
 * 2. 传输层加密由HTTPS/TLS负责，此处不重复实现
 * 
 * 特性：
 * - AES-256-GCM认证加密，防篡改
 * - 每次加密使用随机IV，相同明文产生不同密文
 * - 密钥通过配置注入，支持Vault等密钥管理系统
 */
@Slf4j
@Component
public class DataEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;    // GCM推荐12字节IV
    private static final int GCM_TAG_LENGTH = 128;   // 认证标签长度（位）
    private static final int KEY_LENGTH = 256;        // AES-256

    private final SecretKey secretKey;
    private final SecureRandom secureRandom;

    public DataEncryptor(
            @Value("${rag-wiki.encryption.key:}") String encodedKey,
            @Value("${rag-wiki.encryption.enabled:false}") boolean enabled) {
        
        this.secureRandom = new SecureRandom();
        
        if (enabled && (encodedKey == null || encodedKey.isEmpty())) {
            log.warn("加密已启用但未配置密钥，将自动生成临时密钥（重启后失效）");
            this.secretKey = generateKey();
        } else if (encodedKey != null && !encodedKey.isEmpty()) {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            this.secretKey = new SecretKeySpec(keyBytes, "AES");
        } else {
            // 未启用加密时使用固定占位密钥
            this.secretKey = new SecretKeySpec(new byte[32], "AES");
        }

        log.info("数据加密组件初始化完成, enabled={}", enabled);
    }

    /**
     * 加密明文
     * @param plaintext 明文
     * @return Base64编码的密文（含IV前缀）
     */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            return plaintext;
        }

        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 将IV附加到密文前面: [IV(12bytes)][ciphertext+tag]
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            log.error("加密失败", e);
            throw new RuntimeException("数据加密失败", e);
        }
    }

    /**
     * 解密密文
     * @param encryptedText Base64编码的密文（含IV前缀）
     * @return 明文
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            byte[] combined = Base64.getDecoder().decode(encryptedText);

            // 提取IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, iv.length);

            // 提取密文
            byte[] ciphertext = new byte[combined.length - GCM_IV_LENGTH];
            System.arraycopy(combined, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("解密失败", e);
            throw new RuntimeException("数据解密失败", e);
        }
    }

    /**
     * 生成新的AES-256密钥
     * @return Base64编码的密钥
     */
    public static String generateKeyBase64() {
        SecretKey key = generateKey();
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    private static SecretKey generateKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(KEY_LENGTH, new SecureRandom());
            return keyGenerator.generateKey();
        } catch (Exception e) {
            throw new RuntimeException("密钥生成失败", e);
        }
    }
}
