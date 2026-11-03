package com.qa.autotest.application.execution;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

/**
 * 测试用 SSH 回放客户端（EXE-08）。
 *
 * <p>按命令内容返回 {@code fixtures/dut-replay/} 下的真实形态输出，固定 exit 0、耗时 1ms，
 * 让采集链路可以在本机（无 DUT）被完整验证。失败模式用于构造三类分类断言，
 * 消息形态与真实 OpenSSH 一致，但不含任何口令。
 */
public class ReplaySshClient implements SshClient {

    /** 回放行为：正常 / 不可达 / 认证失败 / 缺工具。 */
    public enum FailureMode {
        NONE,
        UNREACHABLE,
        AUTH_FAILED,
        CMD_NOT_FOUND
    }

    private static final Duration REPLAY_ELAPSED = Duration.ofMillis(1);

    private final FailureMode mode;

    public ReplaySshClient() {
        this(FailureMode.NONE);
    }

    public ReplaySshClient(FailureMode mode) {
        this.mode = mode == null ? FailureMode.NONE : mode;
    }

    @Override
    public CommandResult execute(DutTarget target, String command,
                                 Duration connectTimeout, Duration commandTimeout) {
        String where = target.host() + " port " + target.port();
        switch (mode) {
            case UNREACHABLE:
                return new CommandResult(255, "",
                        "ssh: connect to host " + where + ": Connection timed out", REPLAY_ELAPSED);
            case AUTH_FAILED:
                return new CommandResult(255, "",
                        target.user() + "@" + target.host()
                                + ": Permission denied (publickey,password).", REPLAY_ELAPSED);
            case CMD_NOT_FOUND:
                return new CommandResult(127, "",
                        "bash: line 1: " + firstToken(command) + ": command not found", REPLAY_ELAPSED);
            default:
                return new CommandResult(0, sampleOf(command), "", REPLAY_ELAPSED);
        }
    }

    /** 命令 → 回放样本：三条采集命令各有固定样本，其余命令返回空串。 */
    private static String sampleOf(String command) {
        String normalized = command == null ? "" : command.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("ip -br addr")) {
            return fixture("fixtures/dut-replay/ip-br-addr.out");
        }
        if (normalized.startsWith("ethtool -s")) {
            return fixture("fixtures/dut-replay/ethtool-s.out");
        }
        if (normalized.contains("/proc/interrupts")) {
            return fixture("fixtures/dut-replay/proc-interrupts.out");
        }
        return "";
    }

    private static String firstToken(String command) {
        String trimmed = command == null ? "" : command.trim();
        int space = trimmed.indexOf(' ');
        return space < 0 ? trimmed : trimmed.substring(0, space);
    }

    private static String fixture(String path) {
        try (InputStream in = ReplaySshClient.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("找不到回放样本: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("读取回放样本失败: " + path, ex);
        }
    }
}
