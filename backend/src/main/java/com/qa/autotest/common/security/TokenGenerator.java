package com.qa.autotest.common.security;

import java.security.SecureRandom;
import java.util.Base64;

/** 随机会话令牌生成（32 字节熵）。 */
public final class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenGenerator() {
    }

    public static String newToken() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }
}
