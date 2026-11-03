package com.qa.autotest.application.execution;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 实时日志（EXE-04）：按 runId 分频道，环形缓冲保留最近 2000 行，
 * 100ms 合并推送 ≤50 行，重连补最近 200 行；全量落盘由编排器负责。
 */
@Service
public class RunLogService {

    public static final int RING_CAPACITY = 2000;
    public static final int RECONNECT_LINES = 200;

    public record LogLine(long seq, Instant tsGen, String source, String level, String text) {
    }

    public record Registration(String runId, Runnable closeAction) {

        public void close() {
            closeAction.run();
        }
    }

    private static final class Channel {
        private final AtomicLong seq = new AtomicLong();
        private final LogRingBuffer ring = new LogRingBuffer(RING_CAPACITY);
        private final LogBatcher batcher = new LogBatcher(ring);
        private final List<Consumer<List<LogLine>>> subscribers = new CopyOnWriteArrayList<>();
        private volatile boolean completed;
    }

    private final Map<String, Channel> channels = new ConcurrentHashMap<>();
    private final Clock clock;

    public RunLogService(Clock executionClock) {
        this.clock = executionClock;
    }

    public void publish(String runId, String source, String line) {
        if (runId == null || line == null) {
            return;
        }
        Channel channel = channel(runId);
        channel.batcher.offer(new LogLine(channel.seq.incrementAndGet(),
                clock.instant(), source == null ? "system" : source, levelOf(line), line));
    }

    public List<LogLine> recent(String runId, int max) {
        return channel(runId).batcher.reconnect(max);
    }

    public List<LogLine> drain(String runId, int max) {
        return channel(runId).batcher.drain(max);
    }

    public Registration subscribe(String runId, Consumer<List<LogLine>> subscriber) {
        Channel channel = channel(runId);
        channel.subscribers.add(subscriber);
        List<LogLine> tail = channel.batcher.reconnect(RECONNECT_LINES);
        if (!tail.isEmpty()) {
            subscriber.accept(tail);
        }
        return new Registration(runId,
                () -> channel.subscribers.remove(subscriber));
    }

    public void complete(String runId) {
        Channel channel = channel(runId);
        channel.completed = true;
        push(channel);
    }

    public boolean completed(String runId) {
        return channel(runId).completed;
    }

    /** 每 100ms 推送一批 ≤50 行；没有订阅者时不产生额外开销。 */
    @Scheduled(fixedRate = 100)
    public void flush() {
        channels.values().stream()
                .filter(channel -> !channel.subscribers.isEmpty())
                .forEach(this::push);
    }

    private void push(Channel channel) {
        if (channel.subscribers.isEmpty()) {
            return;
        }
        List<LogLine> batch = channel.batcher.drain(LogBatcher.MAX_PER_FLUSH);
        if (batch.isEmpty()) {
            return;
        }
        for (Consumer<List<LogLine>> subscriber : channel.subscribers) {
            subscriber.accept(batch);
        }
    }

    private Channel channel(String runId) {
        return channels.computeIfAbsent(runId, key -> new Channel());
    }

    private static String levelOf(String line) {
        String upper = line.toUpperCase(Locale.ROOT);
        if (upper.contains("ERROR") || upper.contains("FAIL")) {
            return "ERROR";
        }
        if (upper.contains("WARN")) {
            return "WARN";
        }
        return "INFO";
    }
}
