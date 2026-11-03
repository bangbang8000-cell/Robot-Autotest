package com.qa.autotest.application.execution;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 每 run 一个的环形缓冲（EXE-04）。
 *
 * <p>只保留最近 {@code capacity} 行，重连补最近 200 行时从这里取；
 * {@code total}/{@code dropped} 用于回答「丢了多少」，不靠猜。
 */
public final class LogRingBuffer {

    private final int capacity;
    private final Deque<RunLogService.LogLine> lines;
    private long total;
    private long dropped;

    public LogRingBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity 必须为正数");
        }
        this.capacity = capacity;
        this.lines = new ArrayDeque<>(capacity);
    }

    public synchronized void add(RunLogService.LogLine line) {
        if (line == null) {
            return;
        }
        total++;
        if (lines.size() == capacity) {
            lines.removeFirst();
            dropped++;
        }
        lines.addLast(line);
    }

    public synchronized List<RunLogService.LogLine> recent(int n) {
        if (n <= 0) {
            return List.of();
        }
        List<RunLogService.LogLine> all = new ArrayList<>(lines);
        return all.subList(Math.max(0, all.size() - n), all.size());
    }

    public synchronized long total() {
        return total;
    }

    public synchronized long dropped() {
        return dropped;
    }

    public int capacity() {
        return capacity;
    }
}
