package com.qa.autotest.application.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreflightServiceTest {

    private final PreflightService service = new PreflightService(new ImportSandbox(),
            new PytestImportAdapter(), new RobotImportAdapter());

    private static byte[] zip(String name, String body) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(body.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void pytestPreflightFindsStructureGaps(@TempDir Path dir) throws Exception {
        var report = service.preflight(1L, "pytest", "sample.zip",
                new ByteArrayInputStream(zip("tests/test_a.py", "# ordinary test")), dir);
        assertEquals("pytest", report.sourceType());
        assertEquals(1, report.fileCount());
        assertTrue(report.commands().get(0).contains("--collect-only"));
        assertTrue(report.sensitiveFindings().stream()
                .anyMatch(f -> f.contains("conftest.py")));
    }

    @Test
    void sensitiveContentIsFlagged(@TempDir Path dir) throws Exception {
        var report = service.preflight(1L, "robot", "sample.zip",
                new ByteArrayInputStream(zip("suite.robot", "password=hunter2\n10.1.2.3")), dir);
        assertTrue(report.sensitiveFindings().stream().anyMatch(f -> f.contains("疑似敏感信息")));
        assertFalse(report.executable());
    }

    @Test
    void robotPreflightListsSuite(@TempDir Path dir) throws Exception {
        var report = service.preflight(2L, "robot", "s.zip",
                new ByteArrayInputStream(zip("suite.robot", "*** Test Cases ***")), dir);
        assertTrue(report.commands().get(0).contains("--dryrun"));
        assertEquals(1, report.cases().size());
        assertEquals("ROBOT_SUITE", report.cases().get(0).kind());
    }

    @Test
    void reimportDiffSeparatesAddedRemovedAndConflict() {
        var first = service.diff(9L, "aaa", List.of("a.py", "b.py"));
        assertTrue(first.get("added").contains("a.py"));
        assertTrue(first.get("conflict").isEmpty());
        var second = service.diff(10L, "bbb", List.of("b.py", "c.py"));
        assertTrue(second.get("added").contains("c.py"));
        assertTrue(second.get("removed").isEmpty());
    }

    @Test
    void unsupportedSourceTypeRejected(@TempDir Path dir) {
        assertThrows(IllegalArgumentException.class,
                () -> service.preflight(1L, "junit", "x.zip",
                        new ByteArrayInputStream(new byte[0]), Path.of(dir.toString())));
    }

    private static <T extends Throwable> void assertThrows(Class<T> type, Runnable action) {
        org.junit.jupiter.api.Assertions.assertThrows(type, action::run);
    }
}
