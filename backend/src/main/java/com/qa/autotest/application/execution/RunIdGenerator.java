package com.qa.autotest.application.execution;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * RUN_ID 生成器。格式固定 {@code RUN-yyyyMMddHHmmss-xxxxxx}，时区固定 Asia/Shanghai。
 *
 * <p>末段 6 位十六进制 = <b>实例前缀 2 位 + 同秒单调序列 4 位</b>：
 * <ul>
 *   <li>实例前缀在构造时随机生成，区分多实例部署，避免两台机器同秒从同一序列起跑；</li>
 *   <li>同秒序列保证单实例内绝不重复。旧实现末段为 3 字节 {@link SecureRandom}，
 *       固定时间戳连生成 1000 次约有 2.9% 生日碰撞概率（G1-核验纪要 §3，2026-11-04 现场命中）；</li>
 *   <li>数据库 {@code uk_run_run_id} 唯一索引与 {@code RunService.insertRun} 的冲突回读
 *       是跨实例碰撞的最后一道兜底，本类不重复实现。</li>
 * </ul>
 * 同秒序列溢出（&gt; 65535 次）时明确抛异常，不静默复用旧值。
 */
public final class RunIdGenerator {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT).withZone(ZONE);

    private static final int MAX_SEQUENCE = 0xFFFF;

    private final String instancePrefix;
    private final Object lock = new Object();
    private String lastStamp = "";
    private int sequence = 0;

    public RunIdGenerator() {
        this(randomPrefix());
    }

    /** 包可见构造器：测试用固定前缀，使断言完全确定性、不依赖随机量。 */
    RunIdGenerator(String instancePrefix) {
        if (instancePrefix == null || !instancePrefix.matches("[0-9a-f]{2}")) {
            throw new IllegalArgumentException("instancePrefix 必须是 2 位小写十六进制: " + instancePrefix);
        }
        this.instancePrefix = instancePrefix;
    }

    private static String randomPrefix() {
        byte[] one = new byte[1];
        new SecureRandom().nextBytes(one);
        return String.format(Locale.ROOT, "%02x", one[0] & 0xff);
    }

    /**
     * @param caseId 保留参数，便于后续按用例前缀扩展；当前格式不含 caseId。
     */
    public String next(String caseId, Instant now) {
        String stamp = FORMAT.format(now);
        int seq;
        synchronized (lock) {
            if (!stamp.equals(lastStamp)) {
                lastStamp = stamp;
                sequence = 0;
            }
            seq = sequence++;
        }
        if (seq > MAX_SEQUENCE) {
            throw new IllegalStateException("同一秒 RUN_ID 序列溢出（>65535）: " + stamp);
        }
        return "RUN-" + stamp + "-" + instancePrefix + String.format(Locale.ROOT, "%04x", seq);
    }

    public String nextRequestId() {
        return UUID.randomUUID().toString();
    }
}
