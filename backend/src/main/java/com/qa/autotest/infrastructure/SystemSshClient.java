package com.qa.autotest.infrastructure;

import com.qa.autotest.application.execution.DutTarget;
import com.qa.autotest.application.execution.SshClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 系统 OpenSSH 实现（EXE-08）。
 *
 * <p>只用 JDK {@link ProcessBuilder}，不引入 SSH 库。两条硬约束：
 * <ul>
 *   <li>口令只经 {@code SSHPASS} 环境变量注入（配合 {@code sshpass -e}），argv、异常消息、
 *       返回的 {@link CommandResult} 均不出现明文；</li>
 *   <li>stdout / stderr 各自重定向到临时文件，进程退出后统一读取并删除，
 *       避免未消费的管道把子进程写满而死锁。</li>
 * </ul>
 */
public class SystemSshClient implements SshClient {

    private static final String SSH = "ssh";
    private static final String SSHPASS = "sshpass";

    @Override
    public CommandResult execute(DutTarget target, String command,
                                 Duration connectTimeout, Duration commandTimeout) {
        List<String> argv = buildArgv(target, command, connectTimeout);
        long startedNanos = System.nanoTime();
        Path stdoutFile = null;
        Path stderrFile = null;
        try {
            stdoutFile = Files.createTempFile("dut-ssh-out-", ".log");
            stderrFile = Files.createTempFile("dut-ssh-err-", ".log");

            ProcessBuilder builder = new ProcessBuilder(argv);
            builder.redirectErrorStream(false);
            builder.redirectOutput(stdoutFile.toFile());
            builder.redirectError(stderrFile.toFile());
            if (passwordMode(target)) {
                // 口令只进环境变量，argv 里只有 sshpass -e。
                builder.environment().put("SSHPASS", target.password());
            }

            Process process = builder.start();
            boolean finished = process.waitFor(commandTimeout.toMillis(), TimeUnit.MILLISECONDS);
            String stdout = readQuietly(stdoutFile);
            String stderr = readQuietly(stderrFile);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - startedNanos);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                return new CommandResult(-1, stdout,
                        stderr + "\n[平台] command timed out after "
                                + Math.max(1, commandTimeout.toSeconds()) + "s", elapsed);
            }
            return new CommandResult(process.exitValue(), stdout, stderr, elapsed);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "",
                    "[平台] command timed out after "
                            + Math.max(1, commandTimeout.toSeconds()) + "s",
                    Duration.ofNanos(System.nanoTime() - startedNanos));
        } catch (IOException ex) {
            return new CommandResult(-1, "", "[平台] ssh 启动失败: " + ex.getMessage(),
                    Duration.ofNanos(System.nanoTime() - startedNanos));
        } finally {
            deleteQuietly(stdoutFile);
            deleteQuietly(stderrFile);
        }
    }

    /**
     * 组装 ssh 参数。公钥模式优先：{@code privateKeyPath} 非空即走公钥，口令被忽略。
     */
    static List<String> buildArgv(DutTarget target, String command, Duration connectTimeout) {
        boolean keyMode = keyMode(target);
        boolean pwMode = passwordMode(target);
        List<String> argv = new ArrayList<>();
        if (pwMode) {
            argv.add(SSHPASS);
            argv.add("-e");
        }
        argv.add(SSH);
        if (keyMode) {
            argv.add("-i");
            argv.add(target.privateKeyPath());
        }
        argv.add("-o");
        argv.add("ConnectTimeout=" + Math.max(1, connectTimeout.toSeconds()));
        argv.add("-o");
        argv.add("ServerAliveInterval=10");
        if (keyMode) {
            argv.add("-o");
            argv.add("BatchMode=yes");
        }
        argv.add("-p");
        argv.add(String.valueOf(target.port()));
        if (target.jumpHost() != null && !target.jumpHost().isBlank()) {
            argv.add("-J");
            argv.add(jumpSpec(target));
        }
        argv.add(target.user() + "@" + target.host());
        argv.add(command);
        return argv;
    }

    private static boolean keyMode(DutTarget target) {
        return target.privateKeyPath() != null && !target.privateKeyPath().isBlank();
    }

    private static boolean passwordMode(DutTarget target) {
        return !keyMode(target) && target.password() != null && !target.password().isBlank();
    }

    private static String jumpSpec(DutTarget target) {
        if (target.jumpUser() == null || target.jumpUser().isBlank()) {
            return target.jumpHost();
        }
        return target.jumpUser() + "@" + target.jumpHost();
    }

    private static String readQuietly(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return "";
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 临时文件删除失败不影响采集结论。
        }
    }
}
