package com.qa.autotest.application.importing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImportSandboxTest {

    private final ImportSandbox sandbox = new ImportSandbox();

    private static byte[] zip(String... entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            for (String entry : entries) {
                zos.putNextEntry(new ZipEntry(entry));
                zos.write("content".getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return out.toByteArray();
    }

    @Test
    void twelveUnsafePathsAreRejected() {
        String[] unsafe = {
                "../escape.py", "a/../../escape.py", "/etc/passwd", "\\\\server\\share\\x",
                "C:\\Windows\\x", "~/secret", "a//b.py", "a/./../b.py",
                "a" + (char) 0 + "b.py", "a" + (char) 1 + "b.py", "", "   ",
        };
        for (String path : unsafe) {
            assertFalse(ImportSandbox.isSafeRelativePath(path), "应拒绝: " + path);
        }
    }

    @Test
    void safeRelativePathsAccepted() {
        assertTrue(ImportSandbox.isSafeRelativePath("tests/test_a.py"));
        assertTrue(ImportSandbox.isSafeRelativePath("conftest.py"));
        assertTrue(ImportSandbox.isSafeRelativePath("pkg/sub/resource.resource"));
    }

    @Test
    void zipSlipIsRejectedAsWholeBatch(@TempDir Path dir) throws Exception {
        byte[] bad = zip("../evil.py");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> sandbox.unpack(new ByteArrayInputStream(bad), dir.resolve("out")));
        assertTrue(e.getMessage().contains("不安全") || e.getMessage().contains("逃逸"));
    }

    @Test
    void normalZipLandsReadOnlyWithStableChecksum(@TempDir Path dir) throws Exception {
        byte[] good = zip("tests/test_a.py", "conftest.py");
        Path target = dir.resolve("out");
        List<String> files = sandbox.unpack(new ByteArrayInputStream(good), target);
        assertEquals(2, files.size());
        assertFalse(target.resolve("tests/test_a.py").toFile().canWrite());
        assertEquals(ImportSandbox.checksum(files), ImportSandbox.checksum(files));
        assertEquals(64, ImportSandbox.checksum(files).length());
        assertTrue(Files.isRegularFile(target.resolve("conftest.py")));
    }
}
