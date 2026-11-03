package com.qa.autotest.common.security;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * 凭据加解密（AES-256-GCM，ADR-0006 之外的 AST-03 决策）。
 *
 * <p>密钥来自环境变量 {@code CRED_MASTER_KEY}（32 字节 base64），缺失或长度不对时
 * 构造即失败——启动失败优于「用空密钥加密」。
 *
 * <p>存储形态：{@code base64(IV(12) || ciphertext+tag)}，IV 随机，永不复用。
 */
public final class SecretCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    private SecretCipher(SecretKeySpec key) {
        this.key = key;
    }

    /** 从 base64 密钥构造。 */
    public static SecretCipher fromBase64Key(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("缺少环境变量 CRED_MASTER_KEY（32 字节 base64）");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("CRED_MASTER_KEY 不是合法 base64", ex);
        }
        if (raw.length != 32) {
            throw new IllegalStateException(
                    "CRED_MASTER_KEY 长度必须是 32 字节（当前 " + raw.length + " 字节）");
        }
        return new SecretCipher(new SecretKeySpec(raw, "AES"));
    }

    /** 加密明文，返回可入库的密文字符串。 */
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("凭据加密失败", ex);
        }
    }

    /** 解密；密文损坏或密钥不对时抛 {@link IllegalStateException}。 */
    public String decrypt(String cipherBlob) {
        try {
            byte[] combined = Base64.getDecoder().decode(cipherBlob);
            byte[] iv = new byte[IV_BYTES];
            byte[] encrypted = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            System.arraycopy(combined, IV_BYTES, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("凭据解密失败", ex);
        }
    }
}
