package com.qa.autotest.application.node;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 主控 → 节点任务签名（NODX-03，DEV-PLAN §5.3「带签名任务 JSON」）。
 *
 * <p>密钥 = 节点令牌的 SHA-256（即 {@code token_hash}）：主控只存哈希、可直接做 HMAC key；
 * 节点持有明文令牌，{@code sha256(明文)} 派生同一把 key。**不新增任何密钥存储**。
 * 节点收到任务先验签再执行——签名不对一律 401，不区分「节点不存在 / 签名错」（防枚举）。
 */
public final class TaskSigner {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private TaskSigner() {
    }

    /** SHA-256 的 hex 小写（节点侧派生 HMAC key 用：sha256(明文令牌) = token_hash）。 */
    public static String sha256Hex(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

    /** HMAC-SHA256(key = tokenHash, data = body) 的 hex 小写。 */
    public static String sign(String tokenHash, String body) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(tokenHash.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(
                    mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 不可用", e);
        }
    }

    /** 常量时间比较，避免时序侧信道。 */
    public static boolean verify(String tokenHash, String body, String signature) {
        if (signature == null || signature.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                sign(tokenHash, body).getBytes(StandardCharsets.UTF_8),
                signature.trim().getBytes(StandardCharsets.UTF_8));
    }
}
