package com.qa.autotest.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 凭据加解密单测（FR-v0.2-13，AST-03）。 */
class SecretCipherTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    @DisplayName("加密后可解密且密文每次不同（IV 随机）")
    void 往返一致() {
        SecretCipher cipher = SecretCipher.fromBase64Key(KEY);
        String first = cipher.encrypt("P@ssw0rd!");
        String second = cipher.encrypt("P@ssw0rd!");
        assertNotEquals(first, second);
        assertEquals("P@ssw0rd!", cipher.decrypt(first));
    }

    @Test
    @DisplayName("密钥长度不是 32 字节直接拒绝")
    void 密钥长度校验() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> SecretCipher.fromBase64Key(shortKey));
        assertThrows(IllegalStateException.class, () -> SecretCipher.fromBase64Key(""));
    }

    @Test
    @DisplayName("换密钥后解密失败（不返回明文）")
    void 错误密钥解密失败() {
        SecretCipher writer = SecretCipher.fromBase64Key(KEY);
        byte[] other = new byte[32];
        other[0] = 1;
        SecretCipher reader = SecretCipher.fromBase64Key(Base64.getEncoder().encodeToString(other));
        String blob = writer.encrypt("secret");
        assertThrows(IllegalStateException.class, () -> reader.decrypt(blob));
    }
}
