package com.qa.autotest.application.execution;

import com.qa.autotest.domain.execution.RunRecord;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 队列挑选策略：优先级降序 -> 入队时间升序 -> id 升序；跨项目轮转，避免单项目吃满并发。
 */
public final class RunQueuePolicy {

    private final int maxParallel;

    public RunQueuePolicy(int maxParallel) {
        if (maxParallel < 1) {
            throw new IllegalArgumentException("maxParallel 必须 >= 1");
        }
        this.maxParallel = maxParallel;
    }

    public Comparator<RunRecord> order() {
        return Comparator
                .comparingInt(RunRecord::priority).reversed()
                .thenComparing(RunRecord::enqueuedAt)
                .thenComparingLong(RunRecord::id);
    }

    /**
     * 从待执行集合中挑出本轮可启动的运行，数量不超过 maxParallel - globalRunning。
     * 按项目分组、组内排序、组间轮转，保证新项目不会被老项目的积压饿死。
     */
    public List<RunRecord> pick(List<RunRecord> pending, int globalRunning) {
        int capacity = maxParallel - globalRunning;
        if (capacity <= 0 || pending == null || pending.isEmpty()) {
            return List.of();
        }
        Map<Long, Deque<RunRecord>> byProject = new LinkedHashMap<>();
        pending.stream()
                .sorted(order())
                .forEach(run -> byProject
                        .computeIfAbsent(run.projectId(), key -> new ArrayDeque<>())
                        .addLast(run));

        List<RunRecord> picked = new ArrayList<>(capacity);
        boolean progressed = true;
        while (picked.size() < capacity && progressed) {
            progressed = false;
            for (Deque<RunRecord> queue : byProject.values()) {
                if (picked.size() >= capacity) {
                    break;
                }
                if (!queue.isEmpty()) {
                    picked.add(queue.removeFirst());
                    progressed = true;
                }
            }
        }
        return picked;
    }
}
