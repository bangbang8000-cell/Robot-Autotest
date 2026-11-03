package com.qa.autotest.application.execution;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 推送限流（EXE-04）：每 100ms 取走 ≤50 行；待发堆积超过 20000 行时丢最旧，
 * 并补一行「已省略 N 行」，让前端知道看到的是截断后的流而不是全部。
 */
public final class LogBatcher {

    public static final int MAX_PENDING = 20_000;
    public static final int MAX_PER_FLUSH = 50;

    private final LogRingBuffer ring;
    private final Deque<RunLogService.LogLine> pending = new ArrayDeque<>();
    private long omitted;

    public LogBatcher(LogRingBuffer ring) {
        this.ring = ring;
    }

    public synchronized void offer(RunLogService.LogLine line) {
        if (line == null) {
            return;
        }
        ring.add(line);
        while (pending.size() >= MAX_PENDING) {
            pending.removeFirst();
            omitted++;
        }
        pending.addLast(line);
    }

    public synchronized List<RunLogService.LogLine> drain(int max) {
        int limit = max <= 0 ? MAX_PER_FLUSH : max;
        List<RunLogService.LogLine> out = new ArrayList<>(limit);
        if (omitted > 0) {
            out.add(new RunLogService.LogLine(
                    pending.isEmpty() ? 0 : pending.peekFirst().seq(),
                    Instant.now(), "system", "WARN", "已省略 " + omitted + " 行"));
            omitted = 0;
        }
        while (out.size() < limit && !pending.isEmpty()) {
            out.add(pending.removeFirst());
        }
        return out;
    }

    public synchronized int pendingCount() {
        return pending.size();
    }

    /** 重连补最近 n 行：从环形缓冲取，不要求全量。 */
    public List<RunLogService.LogLine> reconnect(int n) {
        return ring.recent(n);
    }
}
