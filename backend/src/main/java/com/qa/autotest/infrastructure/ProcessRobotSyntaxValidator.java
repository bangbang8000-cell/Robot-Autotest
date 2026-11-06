package com.qa.autotest.infrastructure;

import com.qa.autotest.application.editor.RobotSyntaxValidator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 用 RF 自身的 {@code --dryrun} 做语法校验（EDT-09 / FR-v0.3-23）。
 *
 * <p>命令可配：{@code app.editor.robot-command} 默认 {@code python3 -m robot}。
 * RF 不存在时返回 {@code unchecked}，保存不因此被拦死（否则没有 RF 的环境完全无法编辑）。
 */
@Component
public class ProcessRobotSyntaxValidator implements RobotSyntaxValidator {

    private static final Logger log = LoggerFactory.getLogger(ProcessRobotSyntaxValidator.class);

    /** RF 报错样例：`Error in file '...' on line 5: Non-existing setting 'Foo'.` */
    private static final Pattern LINE = Pattern.compile("on line (\\d+)");
    private static final Pattern ERROR_LINE = Pattern.compile("\\[ ERROR \\]|\\(ERROR\\)|Error in file");

    private final String command;
    private final long timeoutSeconds;

    public ProcessRobotSyntaxValidator(
            @Value("${app.editor.robot-command:python3 -m robot}") String command,
            @Value("${app.editor.syntax-timeout-seconds:30}") long timeoutSeconds) {
        this.command = command;
        this.timeoutSeconds = timeoutSeconds;
    }

    @Override
    public SyntaxReport check(String content) {
        if (content == null || content.isBlank()) {
            return SyntaxReport.pass();
        }
        Path file = null;
        try {
            file = Files.createTempFile("editor-syntax-", ".robot");
            Files.writeString(file, content, StandardCharsets.UTF_8);
            List<String> cmd = new ArrayList<>(List.of(command.trim().split("\\s+")));
            cmd.add("--dryrun");
            cmd.add("--output");
            cmd.add("NONE");
            cmd.add("--log");
            cmd.add("NONE");
            cmd.add("--report");
            cmd.add("NONE");
            cmd.add(file.toAbsolutePath().toString());
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return SyntaxReport.unchecked();
            }
            if (!looksFatal(process.exitValue(), output)) {
                return SyntaxReport.pass();
            }
            return parseFailure(output);
        } catch (IOException ex) {
            // 没装 RF / 命令名不对：按「未校验」处理，不阻断保存
            log.debug("RF 语法校验不可用（{}）：{}", command, ex.getMessage());
            return SyntaxReport.unchecked();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return SyntaxReport.unchecked();
        } finally {
            if (file != null) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                    // 临时文件清理失败不影响保存结论
                }
            }
        }
    }

    /**
     * 是否应当判为语法失败（纯函数，便于单测）。
     *
     * <p><b>为什么不能只看退出码：</b>实测 Robot Framework 7.5.1 对语法错误
     * （未知 section、未知 setting）只打印 {@code [ ERROR ] Error in file '...' on line N}
     * 到 stdout，<b>退出码仍然是 0</b>；只有用例执行失败才返回非 0。
     * 旧实现 {@code exitValue() == 0 → pass} 因此把语法错误全部放行，
     * 编辑器「保存时校验」的功能等于不存在。
     */
    static boolean looksFatal(int exitCode, String output) {
        if (exitCode != 0) {
            return true;
        }
        if (output == null || output.isBlank()) {
            return false;
        }
        for (String raw : output.split("\\R")) {
            if (ERROR_LINE.matcher(raw.trim()).find()) {
                return true;
            }
        }
        return false;
    }

    /** 从 RF 输出里抠行号与错误摘要（纯函数，便于单测）。 */
    public static SyntaxReport parseFailure(String output) {
        if (output == null || output.isBlank()) {
            return SyntaxReport.fail(0, "RF 解析失败（无输出）");
        }
        String message = "";
        int line = 0;
        for (String raw : output.split("\\R")) {
            String row = raw.trim();
            if (row.isEmpty() || !ERROR_LINE.matcher(row).find()) {
                continue;
            }
            message = row;
            Matcher matcher = LINE.matcher(row);
            if (matcher.find()) {
                line = Integer.parseInt(matcher.group(1));
                break;
            }
        }
        if (message.isEmpty()) {
            message = output.trim().split("\\R")[0].trim();
        }
        return SyntaxReport.fail(line, message);
    }
}
