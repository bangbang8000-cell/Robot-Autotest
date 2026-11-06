package com.qa.autotest.application.kernel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 环境探测 / 能力矩阵 / 进程树终止 / 重试超时统一（KRN-05）。 */
@Service
public class EnvironmentProbe {

    private final KernelRegistry registry;

    public EnvironmentProbe(KernelRegistry registry) {
        this.registry = registry;
    }

    public Map<String, Object> capabilities() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String binary : new String[]{"robot", "pabot", "pytest", "python3"}) {
            result.put(binary, onPath(binary));
        }
        result.put("kernels", registry.names());
        result.put("timeoutSeconds", 1800);
        result.put("maxRetries", 2);
        result.put("sameRunMixedKernels", false);
        return result;
    }

    public static boolean onPath(String binary) {
        String path = System.getenv().getOrDefault("PATH", "");
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir, binary);
            if (Files.isExecutable(candidate)) {
                return true;
            }
        }
        return false;
    }

    /** 先终止后代再终止自身，避免留下孤儿 pabot / pytest 子进程。 */
    public int terminateTree(ProcessHandle root) {
        if (root == null || !root.isAlive()) {
            return 0;
        }
        int count = 0;
        for (ProcessHandle child : root.descendants().toList()) {
            if (child.destroyForcibly()) {
                count++;
            }
        }
        if (root.destroyForcibly()) {
            count++;
        }
        return count;
    }
}
