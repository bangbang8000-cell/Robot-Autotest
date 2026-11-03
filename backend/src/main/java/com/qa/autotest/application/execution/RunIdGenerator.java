package com.qa.autotest.application.execution;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * RUN_ID 生成器。格式固定 RUN-yyyyMMddHHmmss-xxxxxx，时区固定 Asia/Shanghai。
 */
public final class RunIdGenerator {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT).withZone(ZONE);

    private final SecureRandom random = new SecureRandom();

    /**
     * @param caseId 保留参数，便于后续按用例前缀扩展；当前格式不含 caseId。
     */
    public String next(String caseId, Instant now) {
        byte[] bytes = new byte[3];
        random.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(6);
        for (byte b : bytes) {
            hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
        }
        return "RUN-" + FORMAT.format(now) + "-" + hex;
    }

    public String nextRequestId() {
        return UUID.randomUUID().toString();
    }
}
