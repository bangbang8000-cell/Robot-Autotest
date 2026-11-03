package com.qa.autotest.application.execution;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * pabot 命令装配。PATH 必须前置 venv/bin，时区与编码固定。
 */
public final class PabotCommandBuilder {

    public PabotCommand build(ShardSpec spec, ExecutionProperties props, Path artifactsRoot) {
        if (spec == null) {
            throw new IllegalArgumentException("分片规格不能为空");
        }
        if (spec.suitePaths() == null || spec.suitePaths().isEmpty()) {
            throw new IllegalArgumentException("suitePaths 不能为空");
        }
        int processes = spec.processes();
        if (processes < 1 || processes > spec.suitePaths().size()) {
            throw new IllegalArgumentException("processes 必须在 1~" + spec.suitePaths().size());
        }
        Path venvBin = ExecutionPaths.resolve(props.getVenvBin());
        Path pabot = venvBin.resolve("pabot");
        if (!Files.exists(pabot)) {
            // 测试环境无 pabot 时仍允许构造命令（由预检拦截）；这里只保证结构完整。
            pabot = venvBin.resolve("pabot");
        }

        Path outputDir = artifactsRoot.resolve("shard-" + spec.shardNo());
        List<String> argv = new ArrayList<>();
        argv.add(pabot.toString());
        argv.add("--outputdir");
        argv.add(outputDir.toString());
        argv.add("--processes");
        argv.add(Integer.toString(processes));
        argv.add("--name");
        argv.add(spec.displayName());
        if (spec.includeTag() != null && !spec.includeTag().isBlank()) {
            argv.add("--include");
            argv.add(spec.includeTag().trim());
        }
        argv.addAll(spec.suitePaths());

        Map<String, String> env = new LinkedHashMap<>();
        String path = props.getVenvBin() == null ? "" : venvBin.toString();
        String original = System.getenv().getOrDefault("PATH", "");
        env.put("PATH", path + File.pathSeparator + original);
        env.put("TZ", "Asia/Shanghai");
        env.put("LANG", "C.UTF-8");
        env.put("PYTHONUNBUFFERED", "1");

        return new PabotCommand(List.copyOf(argv), Map.copyOf(env),
                ExecutionPaths.resolve(props.getWorkspaceRoot()), outputDir);
    }

    public record ShardSpec(int shardNo,
                            List<String> suitePaths,
                            int processes,
                            String includeTag,
                            String displayName) {
    }

    public record PabotCommand(List<String> argv,
                               Map<String, String> environment,
                               Path workDir,
                               Path outputDir) {
    }
}
