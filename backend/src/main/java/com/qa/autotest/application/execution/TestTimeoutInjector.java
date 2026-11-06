package com.qa.autotest.application.execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 单用例超时注入（FR-v0.4-24）。
 *
 * <p>Robot Framework 7.5 没有全局「单用例超时」命令行开关（{@code robot --help} 里没有任何
 * timeout 选项），只有套件级 {@code *** Settings ***} 的 {@code Test Timeout}。
 * 因此在本 run 的独立工作区副本上注入该设置：数据库/仓库里的脚本原文不动，
 * 用户已显式写过 {@code Test Timeout} 的套件保持原值。
 *
 * <p>只在工作区是「按 run 落盘的副本」时调用（见 {@code RunExecutionService}），
 * 回落到真实 workspace-root 时不注入，避免改动用户原脚本。
 *
 * <p>逐用例超时不另造平台字段：RF 原生的用例级 {@code [Timeout]} 由脚本作者在
 * {@code robot_file.content} 里书写，本次只补套件级默认值，改写不会触碰 {@code [Timeout]} 行；
 * RF 语义下用例级 {@code [Timeout]} 优先于套件级 {@code Test Timeout}。
 *
 * <p>只处理 {@code .robot}：{@code .resource} 文件里写 {@code Test Timeout} 会被 RF 判为
 * 「Setting 'Test Timeout' is not allowed in resource file」，资源文件不注入是约束而非取舍。
 */
public final class TestTimeoutInjector {

    private static final String SETTINGS_HEADER = "*** Settings ***";

    private TestTimeoutInjector() {
    }

    /**
     * @param workDir    本 run 的工作区根（pabot 的 cwd）
     * @param suitePaths 本次要执行的套件路径（相对 workDir，或绝对路径）
     * @param seconds    单用例超时秒数；{@code <=0} 表示不注入
     * @return 实际改写的套件文件数
     */
    public static int inject(Path workDir, List<String> suitePaths, int seconds) {
        if (workDir == null || suitePaths == null || seconds <= 0) {
            return 0;
        }
        List<Path> targets = new ArrayList<>();
        for (String raw : suitePaths) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            Path given = Path.of(raw.trim());
            collect(given.isAbsolute() ? given : workDir.resolve(given), targets);
        }
        int changed = 0;
        for (Path file : targets) {
            if (apply(file, seconds)) {
                changed++;
            }
        }
        return changed;
    }

    private static void collect(Path path, List<Path> targets) {
        if (Files.isDirectory(path)) {
            try (Stream<Path> walk = Files.walk(path)) {
                walk.filter(Files::isRegularFile)
                        .filter(TestTimeoutInjector::isRobotFile)
                        .sorted()
                        .forEach(targets::add);
            } catch (IOException ignored) {
                // 目录读失败就跳过；预检与产物校验会兜住「没跑到」的情况。
            }
            return;
        }
        if (Files.isRegularFile(path) && isRobotFile(path)) {
            targets.add(path);
        }
    }

    private static boolean isRobotFile(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".robot");
    }

    /**
     * 只看 {@code *** Settings ***} 段内的 {@code Test Timeout}。
     *
     * <p>不能全文件扫：用例名或关键字名以 {@code Test Timeout} 开头时会被误判成
     * 「用户已配置」，导致整个套件静默不注入。
     */
    private static boolean hasSuiteTimeout(List<String> lines, int header) {
        for (int i = header + 1; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("***")) {
                return false; // 已进入下一个段
            }
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("test timeout")) {
                return true;
            }
        }
        return false;
    }

    /** @return 是否改写了文件 */
    private static boolean apply(Path file, int seconds) {
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return false;
        }
        int header = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).trim().equalsIgnoreCase(SETTINGS_HEADER)) {
                header = i;
                break;
            }
        }
        if (header >= 0 && hasSuiteTimeout(lines, header)) {
            return false; // 用户显式设置优先
        }
        String setting = "Test Timeout    " + seconds + "s";
        List<String> out = new ArrayList<>(lines);
        if (header >= 0) {
            out.add(header + 1, setting);
        } else {
            out.add(0, "");
            out.add(0, setting);
            out.add(0, SETTINGS_HEADER);
        }
        try {
            Files.write(file, out, StandardCharsets.UTF_8);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }
}
