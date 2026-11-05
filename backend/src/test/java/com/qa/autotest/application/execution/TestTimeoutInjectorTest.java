package com.qa.autotest.application.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** FR-v0.4-24：单用例超时靠套件级 `Test Timeout` 注入，且不改用户显式设置。 */
class TestTimeoutInjectorTest {

    @TempDir
    Path tmp;

    private Path suite(String name, String... lines) throws Exception {
        Path dir = tmp.resolve("suites");
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        Files.write(file, List.of(lines), StandardCharsets.UTF_8);
        return file;
    }

    @Test
    void 已有_Settings_段时紧随其后注入() throws Exception {
        Path file = suite("a.robot",
                "*** Settings ***",
                "Library    Collections",
                "",
                "*** Test Cases ***",
                "T1",
                "    Log    1");
        int changed = TestTimeoutInjector.inject(tmp.resolve("suites"), List.of("a.robot"), 30);
        assertEquals(1, changed);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals("Test Timeout    30s", lines.get(1));
    }

    @Test
    void 没有_Settings_段时新建在文件头() throws Exception {
        Path file = suite("b.robot",
                "*** Test Cases ***",
                "T1",
                "    Log    1");
        TestTimeoutInjector.inject(tmp.resolve("suites"), List.of("b.robot"), 45);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals("*** Settings ***", lines.get(0));
        assertEquals("Test Timeout    45s", lines.get(1));
        assertEquals("*** Test Cases ***", lines.get(3));
    }

    @Test
    void 用户已写_Test_Timeout_时不覆盖() throws Exception {
        Path file = suite("c.robot",
                "*** Settings ***",
                "Test Timeout    120s",
                "",
                "*** Test Cases ***",
                "T1",
                "    Log    1");
        int changed = TestTimeoutInjector.inject(tmp.resolve("suites"), List.of("c.robot"), 30);
        assertEquals(0, changed);
        String text = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(text.contains("Test Timeout    120s"));
        assertFalse(text.contains("Test Timeout    30s"));
    }

    @Test
    void 目录递归只改_robot_文件() throws Exception {
        Path dir = tmp.resolve("suites");
        Files.createDirectories(dir.resolve("nested"));
        Files.write(dir.resolve("nested").resolve("d.robot"),
                List.of("*** Settings ***", "Documentation    hi"), StandardCharsets.UTF_8);
        Files.write(dir.resolve("notes.txt"), List.of("*** Settings ***"), StandardCharsets.UTF_8);

        int changed = TestTimeoutInjector.inject(tmp, List.of("suites"), 60);

        assertEquals(1, changed);
        assertTrue(Files.readString(dir.resolve("nested").resolve("d.robot"),
                StandardCharsets.UTF_8).contains("Test Timeout    60s"));
        assertFalse(Files.readString(dir.resolve("notes.txt"),
                StandardCharsets.UTF_8).contains("Test Timeout"));
    }

    @Test
    void 秒数非正时不注入() throws Exception {
        Path file = suite("e.robot", "*** Settings ***", "Documentation    hi");
        assertEquals(0, TestTimeoutInjector.inject(tmp.resolve("suites"), List.of("e.robot"), 0));
        assertFalse(Files.readString(file, StandardCharsets.UTF_8).contains("Test Timeout"));
    }
}
