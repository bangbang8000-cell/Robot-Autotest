package com.qa.autotest.infrastructure;

import com.qa.autotest.application.execution.PabotVersionProbe;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 探测 pabot **包**版本，10 秒超时。
 *
 * <p>为什么不能执行 {@code pabot --version}：pabot 没有自己的 {@code --version} 开关，
 * 它把未知参数原样转发给 robot，输出是 {@code Robot Framework 7.5.1 (Python 3.12.3 on linux)}。
 * 早期实现取这行最后一个空格后的片段，得到 {@code linux)}，再和期望前缀 {@code 5.2} 比较——
 * 必然不匹配，导致**所有运行在预检阶段被拒**（EXE-03 的 PABOT_VERSION 变成永久红灯）。
 *
 * <p>正确做法是问 Python 包元数据：用同一个 venv 里的解释器执行
 * {@code importlib.metadata.version('robotframework-pabot')}。这样拿到的是 pabot 自身版本，
 * 也天然绑定在 {@code <venv>/bin} 这个执行环境上下文里，不会误取系统 Python。
 */
public class PabotVersionProbeImpl implements PabotVersionProbe {

    private static final String QUERY =
            "import importlib.metadata as m;print(m.version('robotframework-pabot'))";

    @Override
    public Optional<String> version(Path pabot) {
        Path python = pythonBeside(pabot);
        if (python == null) {
            return Optional.empty();
        }
        return run(python, "-c", QUERY);
    }

    /** pabot 与解释器在同一 bin 目录；取不到就返回 null，交给预检报"无法获取版本"。 */
    private Path pythonBeside(Path pabot) {
        Path dir = pabot.toAbsolutePath().getParent();
        if (dir == null) {
            return null;
        }
        for (String name : new String[]{"python", "python3"}) {
            Path candidate = dir.resolve(name);
            if (Files.isExecutable(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private Optional<String> run(Path executable, String... args) {
        try {
            String[] command = new String[args.length + 1];
            command[0] = executable.toString();
            System.arraycopy(args, 0, command, 1, args.length);
            Process process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                return Optional.empty();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                if (line == null || line.isBlank()) {
                    return Optional.empty();
                }
                return Optional.of(line.trim());
            }
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
