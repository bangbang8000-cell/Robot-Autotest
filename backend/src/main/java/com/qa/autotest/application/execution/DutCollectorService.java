package com.qa.autotest.application.execution;

import com.qa.autotest.common.BusinessException;
import com.qa.autotest.common.ErrorCode;
import com.qa.autotest.domain.execution.DutCollection;
import com.qa.autotest.domain.execution.DutCollection.CommandOutput;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * DUT 采集服务（EXE-08）：固定三条命令 + 四类失败分类 + 可执行建议。
 *
 * <p>口令只存在 {@link DutTarget} 内并由 {@link SshClient} 的实现处理；本类不打印、
 * 不拼接口令，日志与结论里的目标一律取 {@link DutTarget#toString()}。
 */
@Service
public class DutCollectorService {

    /** 接口名白名单：字母数字与 {@code _ . : -}，防止命令注入。 */
    private static final Pattern IFACE_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]+$");

    private static final String CMD_ADDR = "ip -br addr";
    private static final String CMD_INTERRUPTS = "cat /proc/interrupts";
    private static final int INTERRUPTS_MAX_LINES = 20;

    private final SshClient ssh;
    private final ExecutionProperties props;

    public DutCollectorService(SshClient ssh, ExecutionProperties props) {
        this.ssh = ssh;
        this.props = props;
    }

    public DutCollection collect(DutTarget target, String iface) {
        return collectOutcome(target, iface).collection();
    }

    /** 采集三条命令并按失败文本给出 kind 与 advice；无论成败都带回原始输出。 */
    public DutOutcome collectOutcome(DutTarget target, String iface) {
        String checkedIface = requireIface(iface);
        Duration connectTimeout = Duration.ofSeconds(props.getDut().getConnectTimeoutSeconds());
        Duration commandTimeout = Duration.ofSeconds(props.getDut().getCommandTimeoutSeconds());

        List<String> commands = List.of(CMD_ADDR, "ethtool -S " + checkedIface, CMD_INTERRUPTS);
        Instant startedAt = Instant.now();
        List<CommandOutput> outputs = new ArrayList<>();
        for (String command : commands) {
            SshClient.CommandResult result = ssh.execute(target, command, connectTimeout, commandTimeout);
            String stdout = CMD_INTERRUPTS.equals(command)
                    ? filterInterrupts(result.stdout(), checkedIface)
                    : result.stdout();
            outputs.add(new CommandOutput(command, result.exitCode(), stdout, result.stderr()));
        }
        Instant finishedAt = Instant.now();
        DutCollection collection = new DutCollection(target.toString(), List.copyOf(outputs),
                startedAt, finishedAt);
        Failure failure = classify(outputs);
        return new DutOutcome(failure.kind(), failure.advice(), collection);
    }

    private static String requireIface(String iface) {
        if (iface == null || !IFACE_PATTERN.matcher(iface).matches()) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT,
                    "接口名非法，只允许字母数字与 _ . : -");
        }
        return iface;
    }

    /**
     * {@code /proc/interrupts} 只保留与接口相关的行：始终带表头，匹配行最多补到 20 行。
     * 一行都没匹配上时保留表头与首行，并插入一条平台提示，避免把「没采到」伪装成「没有中断」。
     */
    static String filterInterrupts(String raw, String iface) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        List<String> lines = raw.lines().toList();
        String needle = iface.toLowerCase(Locale.ROOT);
        List<String> matched = lines.stream()
                .filter(line -> line.toLowerCase(Locale.ROOT).contains(needle))
                .toList();
        if (matched.isEmpty()) {
            List<String> fallback = lines.stream().limit(2).toList();
            return "[平台提示] /proc/interrupts 未匹配到 " + iface + " 的行，以下为表头与首行\n"
                    + String.join("\n", fallback);
        }
        String header = lines.get(0);
        if (matched.contains(header)) {
            return String.join("\n", matched.stream().limit(INTERRUPTS_MAX_LINES).toList());
        }
        List<String> kept = new ArrayList<>();
        kept.add(header);
        matched.stream().limit(INTERRUPTS_MAX_LINES - 1).forEach(kept::add);
        return String.join("\n", kept);
    }

    /** 按优先级 unreachable > auth_failed > cmd_not_found > command_failed 汇总三条命令的结论。 */
    private static Failure classify(List<CommandOutput> outputs) {
        for (CommandOutput output : outputs) {
            if (isUnreachable(output)) {
                return new Failure(DutOutcome.KIND_UNREACHABLE, DutOutcome.ADVICE_UNREACHABLE);
            }
        }
        for (CommandOutput output : outputs) {
            if (containsAny(output, "permission denied", "authentication failed")) {
                return new Failure(DutOutcome.KIND_AUTH_FAILED, DutOutcome.ADVICE_AUTH_FAILED);
            }
        }
        for (CommandOutput output : outputs) {
            if (containsAny(output, "command not found", "no such file")) {
                return new Failure(DutOutcome.KIND_CMD_NOT_FOUND, DutOutcome.ADVICE_CMD_NOT_FOUND);
            }
        }
        for (CommandOutput output : outputs) {
            if (output.exitCode() != 0) {
                return new Failure(DutOutcome.KIND_COMMAND_FAILED, DutOutcome.ADVICE_COMMAND_FAILED);
            }
        }
        return new Failure(DutOutcome.KIND_OK, "");
    }

    /**
     * 连通性失败判定：ssh 自身建连失败退出 255；平台侧命令超时由 {@code SystemSshClient}
     * 返回 -1（此时 ssh 还没机会给出 255），两者都算「到不了目标」。
     */
    private static boolean isUnreachable(CommandOutput output) {
        int code = output.exitCode();
        if (code != 255 && code != -1) {
            return false;
        }
        return containsAny(output, "timed out", "could not resolve", "no route");
    }

    private static boolean containsAny(CommandOutput output, String... keywords) {
        String text = (nullToEmpty(output.stdout()) + "\n" + nullToEmpty(output.stderr()))
                .toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Failure(String kind, String advice) {
    }
}
