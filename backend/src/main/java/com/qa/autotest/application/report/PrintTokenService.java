package com.qa.autotest.application.report;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.report.ExportMode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * 打印页一次性令牌（RPT-05）。
 *
 * <p>token 由已登录且通过项目权限校验的接口发放，10 分钟有效，<b>只能用一次</b>
 * （打印页取数时消费）。过期/重复使用一律给中文提示，不返回白屏。
 */
@Service
public class PrintTokenService {

    /** 令牌有效期（DEV-PLAN §5：10 分钟）。 */
    public static final Duration TTL = Duration.ofMinutes(10);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Clock clock;
    private final Map<String, TokenContext> tokens = new ConcurrentHashMap<>();

    public PrintTokenService(Clock clock) {
        this.clock = clock;
    }

    /** 令牌上下文：项目 + RUN_ID + 导出模式。 */
    public record TokenContext(long projectId, String runId, ExportMode mode, Instant expiresAt) {
    }

    /** 发放结果：token 与过期时间。 */
    public record IssuedToken(String token, Instant expiresAt) {
    }

    public IssuedToken issue(long projectId, String runId, ExportMode mode) {
        purgeExpired();
        String token = HexFormat.of().formatHex(randomBytes(32));
        Instant expiresAt = clock.instant().plus(TTL);
        tokens.put(token, new TokenContext(projectId, runId, mode, expiresAt));
        return new IssuedToken(token, expiresAt);
    }

    /**
     * 消费令牌并返回上下文。
     *
     * @param token        打印页 URL 上的 token
     * @param expectedRunId 路径里的 RUN_ID，必须与令牌绑定的运行一致
     */
    public TokenContext consume(String token, String expectedRunId) {
        if (token == null || token.isBlank()) {
            throw invalid("打印链接无效或已过期，请重新发起导出");
        }
        TokenContext context = tokens.remove(token);
        if (context == null) {
            throw invalid("打印链接无效或已过期，请重新发起导出");
        }
        if (!clock.instant().isBefore(context.expiresAt())) {
            throw invalid("打印链接已过期（有效期 10 分钟），请重新发起导出");
        }
        if (expectedRunId == null || !expectedRunId.equals(context.runId())) {
            throw invalid("打印链接与运行号不匹配，请重新发起导出");
        }
        return context;
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        tokens.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
