package com.qa.autotest.application.ai;

import com.qa.autotest.application.kernel.EnvironmentProbe;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * CLI 探测与降级（AIX-02 / C6）。
 *
 * <p>只查 PATH 与可执行位，**不发起任何模型调用**；探测结果决定面板能否进 L2/L3，
 * 没有 CLI 时 L1 静态建议照常可用，绝不报错阻断主功能（R6）。
 */
@Service
public class AiCliProbe {

    private static final List<String> CANDIDATES = List.of("claude", "codex", "gemini", "aider");

    public Map<String, Object> probe() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean anyAvailable = false;
        for (String name : CANDIDATES) {
            boolean found = EnvironmentProbe.onPath(name);
            result.put(name, found);
            anyAvailable |= found;
        }
        result.put("anyAvailable", anyAvailable);
        result.put("maxLevel", anyAvailable ? "L3" : "L1");
        result.put("mode", anyAvailable ? "NORMAL" : "DEGRADED");
        result.put("nonInteractiveOnly", true);
        return result;
    }

    public boolean available() {
        return Boolean.TRUE.equals(probe().get("anyAvailable"));
    }
}
