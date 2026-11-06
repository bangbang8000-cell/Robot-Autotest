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

    /**
     * 订阅者带自己的发送水位（{@code lastSeq}）。
     *
     * <p>没有水位时会出现真实缺陷：连上先同步补发最近 200 行，紧接着下一次
     * 100ms flush 又把还没被取走的同一批行推一遍 —— 客户端收到重复行。
     * 水位让「补发」与「增量」在同一订阅者内天然不重叠，且不影响别的订阅者。
     */
    private static final class Subscriber {
        private final Consumer<List<LogLine>> sink;
        private volatile long lastSeq;

        private Subscriber(Consumer<List<LogLine>> sink) {
            this.sink = sink;
        }

        private void acceptAll(List<LogLine> lines) {
            for (LogLine line : lines) {
                if (line.seq() > lastSeq) {
                    lastSeq = line.seq();
                }
            }
            sink.accept(lines);
        }
    }

    private static final class Channel {
        private final AtomicLong seq = new AtomicLong();
        private final LogRingBuffer ring = new LogRingBuffer(RING_CAPACITY);
        private final LogBatcher batcher = new LogBatcher(ring);
        private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
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
        Subscriber watcher = new Subscriber(subscriber);
        List<LogLine> tail = channel.batcher.reconnect(RECONNECT_LINES);
        if (!tail.isEmpty()) {
            watcher.acceptAll(tail);
        }
        channel.subscribers.add(watcher);
        return new Registration(runId, () -> channel.subscribers.remove(watcher));
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
        for (Subscriber watcher : channel.subscribers) {
            List<LogLine> fresh = new java.util.ArrayList<>(batch.size());
            for (LogLine line : batch) {
                if (line.seq() > watcher.lastSeq) {
                    fresh.add(line);
                }
            }
            if (!fresh.isEmpty()) {
                watcher.acceptAll(fresh);
            }
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
