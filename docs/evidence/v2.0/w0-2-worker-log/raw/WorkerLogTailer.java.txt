package com.qa.autotest.application.execution;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * pabot worker 控制台跟踪（G-B2-1）。
 *
 * <p>{@link PabotOrchestrator} 只读 pabot 主进程的 stdout/stderr，而用例里的
 * {@code Log To Console} 落在 {@code pabot_results/<n>/robot_stdout.out}，从不进入实时频道与
 * {@code run.log}。实测 1 万行输出里 10,000 行在 worker 文件、{@code run.log} 只有 26 行。
 *
 * <p>本类以 250ms 轮询增量读取各 worker 文件（按字节偏移，只解码完整行，UTF-8 多字节不会被截断），
 * 每行以 {@code [worker:<n>] } 前缀交给 {@code sink}（编排器再统一走 emit 的三处落点）。
 * pabot 已以 {@code PYTHONUNBUFFERED=1} 启动，worker stdout 为增量写入，不会攒到进程退出才落盘。
 */
final class WorkerLogTailer {

    private static final String FILE_NAME = "robot_stdout.out";
    private static final long POLL_MILLIS = 250;

    private final Path resultsDir;
    private final Consumer<String> sink;
    private final Map<Path, Tail> tails = new HashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;

    WorkerLogTailer(Path outputDir, Consumer<String> sink) {
        this.resultsDir = outputDir.resolve("pabot_results");
        this.sink = sink;
        this.thread = new Thread(this::loop, "pabot-worker-tail");
        this.thread.setDaemon(true);
    }

    void start() {
        thread.start();
    }

    /** 停止轮询并做最后一次收尾读取（把进程退出前的最后一段输出刷出去）。 */
    void close() {
        running.set(false);
        try {
            thread.join(1000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        poll(true);
    }

    private void loop() {
        while (running.get()) {
            poll(false);
            try {
                Thread.sleep(POLL_MILLIS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void poll(boolean drain) {
        if (!Files.isDirectory(resultsDir)) {
            return;
        }
        try (Stream<Path> files = Files.walk(resultsDir, 2)) {
            files.filter(path -> path.getFileName().toString().equals(FILE_NAME))
                    .sorted()
                    .forEach(file -> readNew(file, drain));
        } catch (IOException ignored) {
            // 分片目录可能在合并阶段被移动/清理；读不到不算执行失败。
        }
    }

    private void readNew(Path file, boolean drain) {
        Tail tail = tails.computeIfAbsent(file, path -> new Tail(workerIndex(path)));
        try {
            long size = Files.size(file);
            if (size < tail.offset) {
                // 文件被截断或重建：从头再读，避免丢掉新内容。
                tail.offset = 0;
                tail.pending = new byte[0];
            }
            if (size == tail.offset) {
                return;
            }
            try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
                channel.position(tail.offset);
                ByteBuffer buffer = ByteBuffer.allocate(8192);
                int read;
                while ((read = channel.read(buffer)) > 0) {
                    buffer.flip();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    buffer.clear();
                    tail.offset += read;
                    consume(tail, bytes, drain);
                }
            }
        } catch (IOException ignored) {
            // 单次读失败下次轮询重试即可。
        }
    }

    /** 只按 '\n' 切分完整行；剩余半行留到下次，避免 UTF-8 多字节被切断。 */
    private void consume(Tail tail, byte[] bytes, boolean drain) {
        byte[] all = new byte[tail.pending.length + bytes.length];
        System.arraycopy(tail.pending, 0, all, 0, tail.pending.length);
        System.arraycopy(bytes, 0, all, tail.pending.length, bytes.length);
        int start = 0;
        for (int i = 0; i < all.length; i++) {
            if (all[i] == '\n') {
                emitLine(tail, all, start, i - start);
                start = i + 1;
            }
        }
        tail.pending = Arrays.copyOfRange(all, start, all.length);
        if (drain && tail.pending.length > 0) {
            emitLine(tail, tail.pending, 0, tail.pending.length);
            tail.pending = new byte[0];
        }
    }

    private void emitLine(Tail tail, byte[] source, int offset, int length) {
        if (length <= 0) {
            return;
        }
        String line = new String(source, offset, length, StandardCharsets.UTF_8);
        if (line.endsWith("\r")) {
            line = line.substring(0, line.length() - 1);
        }
        if (!line.isEmpty()) {
            sink.accept("[worker:" + tail.index + "] " + line);
        }
    }

    private static String workerIndex(Path file) {
        Path parent = file.getParent();
        return parent == null ? "?" : parent.getFileName().toString();
    }

    private static final class Tail {
        private final String index;
        private long offset;
        private byte[] pending = new byte[0];

        private Tail(String index) {
            this.index = index;
        }
    }
}
