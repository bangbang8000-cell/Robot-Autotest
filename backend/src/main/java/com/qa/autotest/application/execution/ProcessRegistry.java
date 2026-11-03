package com.qa.autotest.application.execution;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;

/** 运行中的子进程登记表：取消与超时监控据此按 runId 找到要杀的进程树。 */
@Component
public class ProcessRegistry {

    private final Map<String, List<Process>> byRun = new ConcurrentHashMap<>();

    public void register(String runId, Process process) {
        if (process == null) {
            return;
        }
        byRun.computeIfAbsent(runId, key -> new CopyOnWriteArrayList<>()).add(process);
    }

    public void unregister(String runId) {
        byRun.remove(runId);
    }

    public List<Process> processes(String runId) {
        return List.copyOf(byRun.getOrDefault(runId, List.of()));
    }

    public Set<String> activeRunIds() {
        return Set.copyOf(byRun.keySet());
    }
}
