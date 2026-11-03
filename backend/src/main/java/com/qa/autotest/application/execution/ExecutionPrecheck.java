package com.qa.autotest.application.execution;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 执行前预检。任何一项不满足即拒绝启动，不允许带病执行。
 */
public final class ExecutionPrecheck {

    private final FileSystemProbe files;
    private final PabotVersionProbe versions;

    public ExecutionPrecheck(FileSystemProbe files, PabotVersionProbe versions) {
        this.files = files;
        this.versions = versions;
    }

    public List<Violation> check(PrecheckRequest req) {
        List<Violation> violations = new ArrayList<>();
        Path pabot = req.venvBin() == null ? null : req.venvBin().resolve("pabot");
        if (pabot == null || !files.exists(pabot) || !files.executable(pabot)) {
            violations.add(new Violation("PABOT_NOT_FOUND",
                    "pabot 不存在或不可执行: " + pabot));
        } else {
            String expected = req.pabotVersionPrefix();
            String actual = versions.version(pabot).orElse(null);
            if (actual == null) {
                violations.add(new Violation("PABOT_VERSION", "无法获取 pabot 版本"));
            } else if (expected != null && !expected.isBlank() && !actual.startsWith(expected)) {
                violations.add(new Violation("PABOT_VERSION",
                        "pabot 版本不匹配: 期望 " + expected + "，实际 " + actual));
            }
        }

        if (req.artifactsRoot() == null || !files.writable(req.artifactsRoot())) {
            violations.add(new Violation("ARTIFACT_ROOT_NOT_WRITABLE",
                    "产物目录不可写: " + req.artifactsRoot()));
        } else if (files.usableSpace(req.artifactsRoot()) < req.minFreeBytes()) {
            violations.add(new Violation("DISK_LOW",
                    "可用空间低于阈值: " + files.usableSpace(req.artifactsRoot())
                            + " < " + req.minFreeBytes()));
        }

        if (req.suitePaths() == null || req.suitePaths().isEmpty()) {
            violations.add(new Violation("EMPTY_SUITES", "未指定任何 suite"));
        }
        if (req.availablePorts() < req.requestedConcurrency()) {
            violations.add(new Violation("PORT_SHORTAGE",
                    "可用端口数 " + req.availablePorts() + " < 并发 " + req.requestedConcurrency()));
        }
        if (req.deviceProblems() != null && !req.deviceProblems().isEmpty()) {
            violations.add(new Violation("DEVICE_UNAVAILABLE",
                    "设备不可用: " + String.join("; ", req.deviceProblems())));
        }
        return violations;
    }

    public record PrecheckRequest(Path venvBin,
                                  Path artifactsRoot,
                                  List<String> suitePaths,
                                  int requestedConcurrency,
                                  int availablePorts,
                                  long minFreeBytes,
                                  String pabotVersionPrefix,
                                  List<String> deviceProblems) {
    }

    public record Violation(String code, String message) {
    }
}
