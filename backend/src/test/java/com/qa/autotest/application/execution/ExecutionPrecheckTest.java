package com.qa.autotest.application.execution;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 预检七类违规码：任何一项不满足都必须拒绝启动。 */
class ExecutionPrecheckTest {

    private static final long GIB = 1024L * 1024 * 1024;

    @Test
    void 全部满足时无违规() {
        assertTrue(new ExecutionPrecheck(new FakeFs(), p -> Optional.of("5.2.2"))
                .check(request(new FakeFs(), 4, 32, 5 * GIB, List.of())).isEmpty());
    }

    @Test
    void pabot_不存在报_PABOT_NOT_FOUND() {
        FakeFs fs = new FakeFs();
        fs.exists = false;
        assertCodes(new ExecutionPrecheck(fs, p -> Optional.empty())
                .check(request(fs, 4, 32, 5 * GIB, List.of())), "PABOT_NOT_FOUND");
    }

    @Test
    void 版本前缀不匹配报_PABOT_VERSION() {
        FakeFs fs = new FakeFs();
        assertCodes(new ExecutionPrecheck(fs, p -> Optional.of("4.9.0"))
                .check(request(fs, 4, 32, 5 * GIB, List.of())), "PABOT_VERSION");
    }

    @Test
    void 产物目录不可写报_ARTIFACT_ROOT_NOT_WRITABLE() {
        FakeFs fs = new FakeFs();
        fs.writable = false;
        assertCodes(new ExecutionPrecheck(fs, p -> Optional.of("5.2.2"))
                .check(request(fs, 4, 32, 5 * GIB, List.of())), "ARTIFACT_ROOT_NOT_WRITABLE");
    }

    @Test
    void 空间不足报_DISK_LOW() {
        FakeFs fs = new FakeFs();
        fs.space = GIB;
        assertCodes(new ExecutionPrecheck(fs, p -> Optional.of("5.2.2"))
                .check(request(fs, 4, 32, 5 * GIB, List.of())), "DISK_LOW");
    }

    @Test
    void 空_suite_报_EMPTY_SUITES() {
        FakeFs fs = new FakeFs();
        ExecutionPrecheck.PrecheckRequest req = new ExecutionPrecheck.PrecheckRequest(
                Path.of("/venv/bin"), Path.of("/artifacts"), List.of(), 4, 32, 5 * GIB,
                "5.2", List.of());
        assertCodes(new ExecutionPrecheck(fs, p -> Optional.of("5.2.2")).check(req), "EMPTY_SUITES");
    }

    @Test
    void 端口不足报_PORT_SHORTAGE() {
        FakeFs fs = new FakeFs();
        assertCodes(new ExecutionPrecheck(fs, p -> Optional.of("5.2.2"))
                .check(request(fs, 4, 2, 5 * GIB, List.of())), "PORT_SHORTAGE");
    }

    @Test
    void 设备不可用报_DEVICE_UNAVAILABLE() {
        FakeFs fs = new FakeFs();
        assertCodes(new ExecutionPrecheck(fs, p -> Optional.of("5.2.2"))
                .check(request(fs, 4, 32, 5 * GIB, List.of("GPU-01 连接超时"))),
                "DEVICE_UNAVAILABLE");
    }

    private ExecutionPrecheck.PrecheckRequest request(FakeFs fs, int concurrency, int ports,
                                                      long minFree, List<String> deviceProblems) {
        return new ExecutionPrecheck.PrecheckRequest(
                Path.of("/venv/bin"), Path.of("/artifacts"),
                List.of("suites/a.robot"), concurrency, ports, minFree, "5.2", deviceProblems);
    }

    private void assertCodes(List<ExecutionPrecheck.Violation> violations, String... expected) {
        List<String> codes = new ArrayList<>();
        violations.forEach(v -> codes.add(v.code()));
        assertEquals(List.of(expected), codes);
    }

    private static final class FakeFs implements FileSystemProbe {

        private boolean exists = true;
        private boolean executable = true;
        private boolean writable = true;
        private long space = Long.MAX_VALUE;

        @Override
        public boolean exists(Path path) {
            return exists;
        }

        @Override
        public boolean executable(Path path) {
            return executable;
        }

        @Override
        public boolean writable(Path path) {
            return writable;
        }

        @Override
        public long usableSpace(Path path) {
            return space;
        }
    }
}
