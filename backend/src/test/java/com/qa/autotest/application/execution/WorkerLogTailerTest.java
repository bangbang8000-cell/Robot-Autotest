package com.qa.autotest.application.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** G-B2-1：worker 的 robot_stdout.out 必须实时进入日志通道。 */
class WorkerLogTailerTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("增量读取 worker 控制台并按 [worker:n] 前缀输出")
    void 增量读取worker输出() throws Exception {
        Path workerDir = tempDir.resolve("pabot_results").resolve("0");
        Files.createDirectories(workerDir);
        Path stdout = workerDir.resolve("robot_stdout.out");

        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        WorkerLogTailer tailer = new WorkerLogTailer(tempDir, lines::add);
        tailer.start();

        Files.writeString(stdout, "第一行\n", StandardCharsets.UTF_8);
        awaitLines(lines, 1);
        Files.writeString(stdout, "第二行\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        awaitLines(lines, 2);
        tailer.close();

        assertEquals(List.of("[worker:0] 第一行", "[worker:0] 第二行"), lines);
    }

    @Test
    @DisplayName("末尾半行在关闭时收尾输出，UTF-8 不截断")
    void 收尾刷出半行() throws Exception {
        Path workerDir = tempDir.resolve("pabot_results").resolve("1");
        Files.createDirectories(workerDir);
        Path stdout = workerDir.resolve("robot_stdout.out");
        Files.writeString(stdout, "无换行结尾·中文", StandardCharsets.UTF_8);

        List<String> lines = Collections.synchronizedList(new ArrayList<>());
        WorkerLogTailer tailer = new WorkerLogTailer(tempDir, lines::add);
        tailer.close();

        assertEquals(1, lines.size());
        assertTrue(lines.get(0).endsWith("无换行结尾·中文"));
    }

    private static void awaitLines(List<String> lines, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 3000;
        while (lines.size() < expected && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertTrue(lines.size() >= expected, "等待日志超时，实际 " + lines.size() + " 行");
    }
}
